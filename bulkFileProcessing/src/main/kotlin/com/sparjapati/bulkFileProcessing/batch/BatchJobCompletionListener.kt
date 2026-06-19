package com.sparjapati.bulkFileProcessing.batch

import com.sparjapati.bulkFileProcessing.events.BulkJobCompletionHandler
import com.sparjapati.bulkFileProcessing.events.BulkJobResult
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener

/**
 * Fires after every bulk file processing job finishes, regardless of outcome.
 *
 * Writes the annotated result file via [RowResultCollector], then delegates to
 * [BulkJobCompletionHandler.onJobCompleted] if the [processor] implements it.
 *
 * @param collector the per-job [RowResultCollector] that accumulated rows and errors.
 * @param processor the [FileProcessor] that handled this job; invoked as a
 *   [BulkJobCompletionHandler] if it implements that interface.
 */
class BatchJobCompletionListener(
    private val collector: RowResultCollector,
    private val processor: FileProcessor<*>,
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

        if (processor is BulkJobCompletionHandler) {
            val result = BulkJobResult(
                jobId = jobId,
                processorType = processorType,
                status = jobExecution.status,
                writeCount = writeCount,
                skipCount = skipCount,
                resultFilePath = resultFilePath,
            )
            try {
                processor.onJobCompleted(result)
            } catch (ex: Exception) {
                LOGGER.error(
                    "BulkJobCompletionHandler threw for processorType={} jobId={} — suppressed",
                    processorType, jobId, ex,
                )
                throw ex;
            }
        }
    }
}
