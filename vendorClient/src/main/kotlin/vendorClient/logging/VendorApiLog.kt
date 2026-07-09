package vendorClient.logging

/**
 * Immutable snapshot of a single outbound vendor API call, captured by the logging interceptor.
 *
 * [requestHeaders] and [responseHeaders] map header names to their list of values,
 * matching OkHttp's multi-value header model.
 *
 * [requestId] is shared by every retry attempt of one logical call; [attemptId] is unique per
 * attempt, so rows for the same [requestId] can be told apart.
 */
data class VendorApiLog(
    val apiName: String,
    val requestId: String,
    val attemptId: String,
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
