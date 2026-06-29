package com.sparjapati.vendorClient.interceptor

import com.sparjapati.vendorClient.annotation.VendorApiAnnotationResolver
import com.sparjapati.vendorClient.config.VendorClientSettings
import com.sparjapati.vendorClient.logging.VendorApiLog
import com.sparjapati.vendorClient.logging.VendorApiLogSink
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
 * **Request ID**: read from the outbound request header named [VendorClientSettings.requestIdHeader]
 * — this is whatever value [TraceForwardingInterceptor] already stamped on the request, so the two
 * interceptors must be ordered with [TraceForwardingInterceptor] first.
 *
 * @param settings global settings for header names and sensitive header set
 * @param logSink persistence port; called once per request (even on exception)
 */
class VendorApiLoggingInterceptor(
    private val settings: VendorClientSettings,
    private val logSink: VendorApiLogSink,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val api = VendorApiAnnotationResolver.resolve(request)
            ?: return chain.proceed(request)
        val apiName = api.name
        val requestId = request.header(settings.requestIdHeader) ?: ""

        val requestBody = request.body?.let { body ->
            if (isBinary(body.contentType()?.toString())) {
                "binary response only"
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
                "binary response only"
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
