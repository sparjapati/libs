package com.sparjapati.bulkFileProcessing.batch

import com.sparjapati.bulkFileProcessing.events.BulkJobCompletionHandler
import com.sparjapati.bulkFileProcessing.events.BulkJobResult
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener

/**
 * Fires after every bulk file processing job finishes, regardless of outcome.
 *
 * Writes the annotated result file via [RowResultCollector], then calls
 * [BulkJobCompletionHandler.onJobCompleted] if a handler was registered for this job's
 * processor type. The handler is resolved once at job creation time by
 * [FileProcessingJobFactory] and passed in directly — this listener has no registry dependency.
 *
 * @param collector the per-job [RowResultCollector] that accumulated rows and errors.
 * @param handler   the completion handler for this processor type, or `null` if none registered.
 */
class BatchJobCompletionListener(
    private val collector: RowResultCollector,
    private val handler: BulkJobCompletionHandler?,
) : JobExecutionListener {

    companion object {
        const val JOB_PARAM_JOB_ID = "jobId"
        const val JOB_PARAM_PROCESSOR_TYPE = "processorType"
        private val LOGGER = LoggerFactory.getLogger(BatchJobCompletionListener::class.java)
    }

    override fun afterJob(jobExecution: JobExecution) {
        val params = jobExecution.jobParameters
        val jobId = params.getString(JOB_PARAM_JOB_ID) ?: "unknown"
        val processorType = params.getString(JOB_PARAM_PROCESSOR_TYPE) ?: "unknown"
        val writeCount = jobExecution.stepExecutions.sumOf { it.writeCount }
        val skipCount = jobExecution.stepExecutions.sumOf { it.skipCount }

        val resultFilePath = collector.writeResultFile()

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
}
