package com.sparjapati.vendorClient.interceptor

import com.sparjapati.vendorClient.annotation.VendorApiAnnotationResolver
import com.sparjapati.vendorClient.config.VendorClientSettings
import com.sparjapati.vendorClient.logging.VendorApiLog
import com.sparjapati.vendorClient.logging.VendorApiLogSink
import com.sparjapati.vendorClient.logging.maskSensitive
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

/**
 * Captures a structured [VendorApiLog] for every outbound vendor API call and forwards it to
 * [logSink] for persistence.
 *
 * **Request body buffering**: the body is read into a [Buffer] before the call proceeds so it can
 * be captured for the log without consuming the stream that OkHttp needs.
 *
 * **Exception safety**: when [chain.proceed] throws, the log is saved with `success = false` and
 * `errorMessage = e.message` before the exception is rethrown. The log is always saved exactly once.
 *
 * **Request ID**: read from the outbound request header named [VendorClientSettings.requestIdHeader]
 * — this is whatever value [TraceForwardingInterceptor] already stamped on the request, so the two
 * interceptors must be ordered with [TraceForwardingInterceptor] first.
 *
 * **Sensitive headers**: both request and response headers are passed through [maskSensitive] with
 * [VendorClientSettings.sensitiveHeaders] before logging.
 *
 * @param settings global settings for header names, body size cap, and sensitive header set
 * @param logSink persistence port; called once per request (even on exception)
 */
class VendorApiLoggingInterceptor(
    private val settings: VendorClientSettings,
    private val logSink: VendorApiLogSink,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val apiName = VendorApiAnnotationResolver.resolve(request)?.name ?: "UNKNOWN"
        val requestId = request.header(settings.requestIdHeader) ?: ""

        val requestBody = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readByteString(minOf(buffer.size, settings.apiLogMaxBodyBytes)).utf8()
        }

        val start = System.currentTimeMillis()
        return try {
            val response = chain.proceed(request)
            val durationMs = System.currentTimeMillis() - start
            val responseBody = response.peekBody(settings.apiLogMaxBodyBytes)?.string()

            logSink.save(
                VendorApiLog(
                    apiName = apiName,
                    requestId = requestId,
                    httpMethod = request.method,
                    url = request.url.toString(),
                    requestHeaders = request.headers.toMultimap().maskSensitive(settings.sensitiveHeaders),
                    requestBody = requestBody,
                    responseCode = response.code,
                    responseHeaders = response.headers.toMultimap().maskSensitive(settings.sensitiveHeaders),
                    responseBody = responseBody,
                    success = response.isSuccessful,
                    errorMessage = null,
                    durationMs = durationMs,
                )
            )
            response
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - start
            logSink.save(
                VendorApiLog(
                    apiName = apiName,
                    requestId = requestId,
                    httpMethod = request.method,
                    url = request.url.toString(),
                    requestHeaders = request.headers.toMultimap().maskSensitive(settings.sensitiveHeaders),
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
}
