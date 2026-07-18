package com.bulkFileProcessing.batch

import com.bulkFileProcessing.events.BulkJobCompletionHandler
import com.bulkFileProcessing.events.BulkJobResult
import com.bulkFileProcessing.jobstore.BulkJobRecord
import com.bulkFileProcessing.jobstore.BulkJobStore
import com.bulkFileProcessing.jobstore.JobId
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener

/**
 * Fires after every bulk file processing job finishes, regardless of outcome.
 *
 * Writes the annotated result file via [ResultFileWriter], records the final outcome in
 * [jobStore], then calls [BulkJobCompletionHandler.onJobCompleted] if a handler was
 * registered for this job's processor type. The handler is resolved once at job creation
 * time by [FileProcessingJobFactory] and passed in directly — this listener has no
 * registry dependency.
 *
 * @param writer        the per-job [ResultFileWriter] that produces the annotated output file.
 * @param handler       the completion handler for this processor type, or `null` if none registered.
 * @param jobStore      receives the final [BulkJobRecord] for this job run.
 * @param record the STARTED [BulkJobRecord] built by [BatchJobService.launch] and carried through
 *                      by [FileProcessingJobFactory]; the final record is derived from it via [BulkJobRecord.copy]
 *                      so unchanged fields (jobId, processorType, originalFileName, startedAt) aren't restated.
 */
class BatchJobCompletionListener(
    private val writer: ResultFileWriter,
    private val handler: BulkJobCompletionHandler?,
    private val jobStore: BulkJobStore,
    private val record: BulkJobRecord,
) : JobExecutionListener {

    companion object {
        const val JOB_PARAM_JOB_ID = "jobId"
        const val JOB_PARAM_PROCESSOR_TYPE = "processorType"
        private val LOGGER = LoggerFactory.getLogger(BatchJobCompletionListener::class.java)
    }

    override fun afterJob(jobExecution: JobExecution) {
        val params = jobExecution.jobParameters
        val jobId: JobId = params.getString(JOB_PARAM_JOB_ID) ?: "unknown"
        val processorType: ProcessorType = params.getString(JOB_PARAM_PROCESSOR_TYPE) ?: "unknown"
        val writeCount = jobExecution.stepExecutions.sumOf { it.writeCount }
        val skipCount = jobExecution.stepExecutions.sumOf { it.skipCount }

        val resultFilePath = writer.write()
        LOGGER.info(
            "Bulk job completed jobId={} processorType={} status={} writeCount={} skipCount={} resultFile={}",
            jobId, processorType, jobExecution.status, writeCount, skipCount, resultFilePath,
        )

        val errorMessage = jobExecution.allFailureExceptions
            .firstOrNull()
            ?.message
            .takeIf { jobExecution.status == BatchStatus.FAILED }

        trySave(
            record.copy(
                status = jobExecution.status,
                writeCount = writeCount,
                skipCount = skipCount,
                resultFilePath = resultFilePath,
                errorMessage = errorMessage,
                completedAt = System.currentTimeMillis(),
            ),
        )

        if (handler == null) {
            LOGGER.debug("No BulkJobCompletionHandler registered for processorType={} jobId={}", processorType, jobId)
            return
        }
        val result = BulkJobResult(
            jobId = jobId,
            processorType = processorType,
            status = jobExecution.status,
            writeCount = writeCount,
            skipCount = skipCount,
            resultFilePath = resultFilePath,
        )
        try {
            handler.onJobCompleted(result)
        } catch (ex: Exception) {
            LOGGER.error(
                "BulkJobCompletionHandler threw for processorType={} jobId={} — suppressed",
                processorType, jobId, ex,
            )
            throw ex
        }
    }

    private fun trySave(record: BulkJobRecord) {
        try {
            jobStore.save(record)
        } catch (ex: Exception) {
            LOGGER.error(
                "BulkJobStore.save failed for jobId={} processorType={} status={} — job continues, record not persisted",
                record.jobId, record.processorType, record.status, ex,
            )
        }
    }
}
