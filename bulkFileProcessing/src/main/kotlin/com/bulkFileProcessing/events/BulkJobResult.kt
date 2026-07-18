package com.bulkFileProcessing.events

import com.bulkFileProcessing.batch.ProcessorType
import com.bulkFileProcessing.jobstore.JobId
import org.springframework.batch.core.BatchStatus

/**
 * Outcome of a completed bulk file processing job, passed to
 * [BulkJobCompletionHandler.onJobCompleted].
 *
 * @param jobId          unique identifier of the completed job (for tracing).
 * @param processorType  the [com.bulkFileProcessing.batch.FileProcessor.processorType]
 *   that handled the file.
 * @param status         final [BatchStatus] of the job (e.g. COMPLETED, FAILED).
 * @param writeCount     total number of rows successfully written.
 * @param skipCount      total number of rows skipped due to errors.
 * @param resultFilePath absolute path to the annotated result file, or `null` if no rows were read.
 */
data class BulkJobResult(
    val jobId: JobId,
    val processorType: ProcessorType,
    val status: BatchStatus,
    val writeCount: Long,
    val skipCount: Long,
    val resultFilePath: String?,
)
