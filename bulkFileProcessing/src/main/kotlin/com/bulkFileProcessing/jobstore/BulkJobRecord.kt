package com.bulkFileProcessing.jobstore

import com.bulkFileProcessing.batch.ProcessorType
import org.springframework.batch.core.BatchStatus

/** Identifier of a single bulk file processing job run. */
typealias JobId = String

/**
 * Persisted record of a single bulk file processing job run, queryable via [BulkJobStore].
 *
 * @param jobId            unique identifier of the job run.
 * @param processorType    the [com.bulkFileProcessing.batch.FileProcessor.processorType] that handled the file.
 * @param status           [BatchStatus.STARTED] while running; a terminal status
 *                         ([BatchStatus.COMPLETED], [BatchStatus.FAILED], etc.) once finished.
 * @param writeCount       rows successfully written so far (0 while [status] is [BatchStatus.STARTED]).
 * @param skipCount        rows skipped due to errors so far (0 while [status] is [BatchStatus.STARTED]).
 * @param resultFilePath   absolute path to the annotated result file, or `null` before completion or
 *                         if no rows were read.
 * @param errorMessage     failure reason; non-null only when [status] is [BatchStatus.FAILED].
 * @param originalFileName the original uploaded filename.
 * @param startedAt        epoch millis when the job was launched.
 * @param completedAt      epoch millis when the job reached a terminal status, or `null` while running.
 */
data class BulkJobRecord(
    val jobId: JobId,
    val processorType: ProcessorType,
    val status: BatchStatus,
    val writeCount: Long,
    val skipCount: Long,
    val resultFilePath: String?,
    val errorMessage: String?,
    val originalFileName: String,
    val startedAt: Long,
    val completedAt: Long?,
) {
    /** Returns a copy marked [BatchStatus.FAILED] with [errorMessage] and [completedAt] set. */
    fun markFailed(errorMessage: String, completedAt: Long): BulkJobRecord =
        copy(status = BatchStatus.FAILED, errorMessage = errorMessage, completedAt = completedAt)
}
