package com.sparjapati.bulkFileProcessing.dtos

/**
 * Response returned immediately after a bulk upload request is accepted.
 *
 * @param jobId   unique identifier for the background batch job, useful for tracing in logs.
 * @param message human-readable confirmation that processing has started.
 */
data class BulkUploadResponse(
    val jobId: String,
    val message: String,
)
