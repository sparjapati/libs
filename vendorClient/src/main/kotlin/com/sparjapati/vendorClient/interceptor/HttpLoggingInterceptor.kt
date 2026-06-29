package com.sparjapati.vendorClient.interceptor

import com.sparjapati.vendorClient.config.DEFAULT_SENSITIVE_HEADERS
import com.sparjapati.vendorClient.logging.BINARY_BODY_PLACEHOLDER
import com.sparjapati.vendorClient.logging.maskSensitive
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import okio.GzipSource
import kotlin.time.TimeSource

/**
 * A logging interceptor that writes HTTP request and response details to an arbitrary log sink.
 *
 * **Masking**: sensitive headers (determined by [sensitiveHeaders]) are masked before logging.
 * Raw header values are never written to the log.
 *
 * **Full body**: no size limit is applied. The complete body is always logged (or `"binary response only"`
 * for binary content types).
 *
 * **Binary detection**: content-types that are not text, JSON, XML, or form-encoded are treated as
 * binary; the literal string `"binary response only"` is logged in place of the body bytes.
 *
 * **Gzip**: gzip-compressed responses are decompressed transparently before logging.
 *
 * **Timing**: elapsed time is measured using [TimeSource.Monotonic] for monotonic accuracy.
 *
 * @param log sink function; each intercepted request writes one multi-line string per HTTP exchange
 * @param level controls verbosity — [Level.NONE] disables all output, [Level.HEADERS] logs
 *   request/response line and headers only, [Level.BODY] also logs the full body
 * @param sensitiveHeaders header names (case-insensitive) whose values are replaced with `***` in output
 */
class HttpLoggingInterceptor(
    private val log: (String) -> Unit,
    var level: Level = Level.BODY,
    private val sensitiveHeaders: Set<String> = DEFAULT_SENSITIVE_HEADERS,
) : Interceptor {

    enum class Level { NONE, HEADERS, BODY }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (level == Level.NONE) return chain.proceed(chain.request())

        val request = chain.request()
        val maskedRequestHeaders = request.headers.toMultimap().maskSensitive(sensitiveHeaders)

        val sb = StringBuilder()
        sb.append("--> ${request.method} ${request.url}\n")
        maskedRequestHeaders.forEach { (name, values) ->
            values.forEach { value -> sb.append("$name: $value\n") }
        }

        if (level == Level.BODY) {
            request.body?.let { body ->
                if (!isBinary(body.contentType()?.toString())) {
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    sb.append("\n${buffer.readUtf8()}\n")
                } else {
                    sb.append("\n$BINARY_BODY_PLACEHOLDER\n")
                }
            }
        }

        val mark = TimeSource.Monotonic.markNow()
        val response = chain.proceed(request)
        val durationMs = mark.elapsedNow().inWholeMilliseconds

        val maskedResponseHeaders = response.headers.toMultimap().maskSensitive(sensitiveHeaders)

        val sb2 = StringBuilder()
        sb2.append("<-- ${response.code} ${response.message} ${request.url} (${durationMs}ms)\n")
        maskedResponseHeaders.forEach { (name, values) ->
            values.forEach { value -> sb2.append("$name: $value\n") }
        }

        if (level == Level.BODY && response.body != null) {
            val body = response.peekBody(Long.MAX_VALUE)
            val contentEncoding = response.header("Content-Encoding")
            val raw = body?.bytes() ?: ByteArray(0)
            val text = when {
                isBinary(response.body?.contentType()?.toString()) -> BINARY_BODY_PLACEHOLDER
                contentEncoding == "gzip" -> {
                    val buf = Buffer().write(raw)
                    val gzip = Buffer()
                    GzipSource(buf).use { it.read(gzip, Long.MAX_VALUE) }
                    gzip.readUtf8()
                }
                else -> String(raw)
            }
            sb2.append("\n$text\n")
        }

        log(sb.toString() + "\n" + sb2.toString())
        return response
    }

    private fun isBinary(contentType: String?): Boolean {
        if (contentType == null) return false
        val lower = contentType.lowercase()
        return "text/" !in lower && "json" !in lower && "xml" !in lower && "form" !in lower
    }
}
