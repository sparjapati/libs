package com.sparjapati.bulkFileProcessing.events

import org.springframework.batch.core.BatchStatus

/**
 * Published by [com.sparjapati.bulkFileProcessing.batch.BatchJobCompletionListener]
 * after every bulk file processing job finishes, regardless of outcome.
 *
 * @param jobId          unique identifier of the completed job (for tracing).
 * @param processorType  the [com.sparjapati.bulkFileProcessing.batch.FileProcessor.processorType]
 *   that handled the file.
 * @param userId         ID of the user who triggered the upload.
 * @param status         final [BatchStatus] of the job (e.g. COMPLETED, FAILED).
 * @param writeCount     total number of rows successfully written.
 * @param skipCount      total number of rows skipped due to errors.
 * @param resultFilePath absolute path to the annotated result file written by
 *   [com.sparjapati.bulkFileProcessing.batch.RowResultCollector],
 *   or `null` if no rows were read.
 */
data class BatchJobCompletedEvent(
    val jobId: String,
    val processorType: String,
    val userId: String,
    val status: BatchStatus,
    val writeCount: Long,
    val skipCount: Long,
    val resultFilePath: String?,
)
