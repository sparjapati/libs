package com.sparjapati.vendorClient.logging

/**
 * Immutable snapshot of a single outbound vendor API call, captured by the logging interceptor.
 *
 * [requestHeaders] and [responseHeaders] map header names to their list of values,
 * matching OkHttp's multi-value header model.
 */
data class VendorApiLog(
    val apiName: String,
    val requestId: String,
    val httpMethod: String,
    val url: String,
    val requestHeaders: Map<String, List<String>> = emptyMap(),
    val requestBody: String? = null,
    val responseCode: Int? = null,
    val responseHeaders: Map<String, List<String>> = emptyMap(),
    val responseBody: String? = null,
    val success: Boolean,
    val errorMessage: String? = null,
    val durationMs: Long,
)
