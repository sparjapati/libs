package vendorClient.interceptor

import vendorClient.annotation.VendorApiAnnotationResolver
import vendorClient.logging.BINARY_BODY_PLACEHOLDER
import vendorClient.logging.VendorApiLog
import vendorClient.logging.VendorApiLogSink
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import kotlin.time.TimeSource

/**
 * Captures a structured [VendorApiLog] for every outbound vendor API call and forwards it to
 * [logSink] for persistence.
 *
 * **Request body buffering**: the full body is read into a [Buffer] before the call proceeds so it
 * can be captured for the log without consuming the stream that OkHttp needs. No size limit is applied.
 *
 * **Binary detection**: if the content-type indicates binary content (not text, JSON, XML, or form),
 * the body is stored as the literal string `"binary response only"` rather than raw bytes.
 *
 * **Headers saved raw**: request and response headers are saved without any masking — the raw values
 * are always persisted to the sink for audit purposes. Masking is the caller's responsibility if
 * downstream storage requires it.
 *
 * **Exception safety**: when [chain.proceed] throws, the log is saved with `success = false` and
 * `errorMessage = e.message` before the exception is rethrown. The log is always saved exactly once.
 *
 * **Request ID**: sourced from [requestIdProvider] rather than the outbound header, so it always
 * reflects the original inbound Spring request ID rather than the per-attempt trace ID that
 * [TraceForwardingInterceptor] appends.
 *
 * @param logSink persistence port; called once per request (even on exception)
 * @param requestIdProvider returns the current inbound request ID; defaults to `{ null }` (empty string stored)
 */
class VendorApiLoggingInterceptor(
    private val logSink: VendorApiLogSink,
    private val requestIdProvider: () -> String? = { null },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val api = VendorApiAnnotationResolver.resolve(request)
            ?: return chain.proceed(request)
        val apiName = api.name
        val requestId = requestIdProvider() ?: ""

        val requestBody = request.body?.let { body ->
            if (isBinary(body.contentType()?.toString())) {
                BINARY_BODY_PLACEHOLDER
            } else {
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            }
        }

        val mark = TimeSource.Monotonic.markNow()
        return try {
            val response = chain.proceed(request)
            val durationMs = mark.elapsedNow().inWholeMilliseconds
            val responseBody = if (isBinary(response.body?.contentType()?.toString())) {
                BINARY_BODY_PLACEHOLDER
            } else {
                response.peekBody(Long.MAX_VALUE)?.string()
            }

            logSink.save(
                VendorApiLog(
                    apiName = apiName,
                    requestId = requestId,
                    httpMethod = request.method,
                    url = request.url.toString(),
                    requestHeaders = request.headers.toMultimap(),
                    requestBody = requestBody,
                    responseCode = response.code,
                    responseHeaders = response.headers.toMultimap(),
                    responseBody = responseBody,
                    success = response.isSuccessful,
                    errorMessage = null,
                    durationMs = durationMs,
                )
            )
            response
        } catch (e: Exception) {
            val durationMs = mark.elapsedNow().inWholeMilliseconds
            logSink.save(
                VendorApiLog(
                    apiName = apiName,
                    requestId = requestId,
                    httpMethod = request.method,
                    url = request.url.toString(),
                    requestHeaders = request.headers.toMultimap(),
                    requestBody = requestBody,
                    responseCode = null,
                    responseHeaders = emptyMap(),
                    responseBody = null,
                    success = false,
                    errorMessage = e.message,
                    durationMs = durationMs,
                )
            )
            throw e
        }
    }

    private fun isBinary(contentType: String?): Boolean {
        if (contentType == null) return false
        val lower = contentType.lowercase()
        return "text/" !in lower && "json" !in lower && "xml" !in lower && "form" !in lower
    }
}
