package com.sparjapati.bulkFileProcessing.batch

import com.sparjapati.bulkFileProcessing.events.BatchJobCompletedEvent
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.context.ApplicationEventPublisher

/**
 * Fires after every bulk file processing job finishes, regardless of outcome.
 *
 * Writes the annotated result file via [RowResultCollector] (all rows with an extra
 * `error` column populated for skipped rows), then publishes a [BatchJobCompletedEvent]
 * so downstream consumers can notify users, upload files, etc.
 *
 * @param collector      the per-job [RowResultCollector] that accumulated rows and errors.
 * @param eventPublisher Spring's event publisher; used to fire [BatchJobCompletedEvent].
 */
class BatchJobCompletionListener(
    private val collector: RowResultCollector,
    private val eventPublisher: ApplicationEventPublisher,
) : JobExecutionListener {

    companion object {
        const val JOB_PARAM_JOB_ID = "jobId"
        const val JOB_PARAM_USER_ID = "userId"
        const val JOB_PARAM_PROCESSOR_TYPE = "processorType"
    }

    override fun afterJob(jobExecution: JobExecution) {
        val params = jobExecution.jobParameters
        val jobId = params.getString(JOB_PARAM_JOB_ID) ?: "unknown"
        val userId = params.getString(JOB_PARAM_USER_ID) ?: "unknown"
        val processorType = params.getString(JOB_PARAM_PROCESSOR_TYPE) ?: "unknown"
        val writeCount = jobExecution.stepExecutions.sumOf { it.writeCount }
        val skipCount = jobExecution.stepExecutions.sumOf { it.skipCount }

        val resultFilePath = collector.writeResultFile()

        eventPublisher.publishEvent(
            BatchJobCompletedEvent(
                jobId = jobId,
                processorType = processorType,
                userId = userId,
                status = jobExecution.status,
                writeCount = writeCount,
                skipCount = skipCount,
                resultFilePath = resultFilePath,
            )
        )
    }
}
