package vendorClient.interceptor

import vendorClient.VendorApiKey
import vendorClient.annotation.TraceableApi
import vendorClient.config.VendorClientSettings
import vendorClient.logging.BINARY_BODY_PLACEHOLDER
import vendorClient.logging.VendorApiLog
import vendorClient.logging.VendorApiLogSink
import io.mockk.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Invocation
import java.io.IOException
import kotlin.test.*

class VendorApiLoggingInterceptorTest {

    enum class TestApi : VendorApiKey { MY_API }

    private val capturedLog = CapturingSlot<VendorApiLog>()
    private val sink = VendorApiLogSink { log -> capturedLog.captured = log }
    private val settings = VendorClientSettings()

    private fun chain(
        responseCode: Int = 200,
        throwError: Exception? = null,
        contentType: String = "application/json",
        requestContentType: String = "application/json",
        extraHeaders: Map<String, String> = emptyMap(),
    ): Interceptor.Chain {
        val method: java.lang.reflect.Method = mockk()
        every { method.getAnnotation(TraceableApi::class.java) } returns TraceableApi(TestApi::class, "MY_API")
        val invocation: Invocation = mockk()
        every { invocation.method() } returns method

        val requestBuilder = Request.Builder()
            .url("https://example.com/path")
            .header("X-Request-Id", "req-abc")
            .post("""{"test":true}""".toRequestBody(requestContentType.toMediaType()))
            .tag(Invocation::class.java, invocation)

        extraHeaders.forEach { (name, value) -> requestBuilder.header(name, value) }

        val request = requestBuilder.build()

        val chain: Interceptor.Chain = mockk()
        every { chain.request() } returns request
        if (throwError != null) {
            every { chain.proceed(any()) } throws throwError
        } else {
            val body = """{"ok":true}""".toResponseBody(contentType.toMediaType())
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(responseCode).message("OK").body(body).build()
            every { chain.proceed(any()) } returns response
        }
        return chain
    }

    @Test fun `logs successful response`() {
        VendorApiLoggingInterceptor(settings = settings, logSink = sink, requestIdProvider = { "req-abc" })
            .intercept(chain(200))
        assertEquals("MY_API", capturedLog.captured.apiName)
        assertEquals("req-abc", capturedLog.captured.requestId)
        assertTrue(capturedLog.captured.success)
        assertEquals(200, capturedLog.captured.responseCode)
    }

    @Test fun `logs failed response (4xx) as not success`() {
        VendorApiLoggingInterceptor(settings, sink).intercept(chain(404))
        assertFalse(capturedLog.captured.success)
        assertEquals(404, capturedLog.captured.responseCode)
    }

    @Test fun `logs thrown exception and rethrows`() {
        val interceptor = VendorApiLoggingInterceptor(settings, sink)
        assertFailsWith<IOException> { interceptor.intercept(chain(throwError = IOException("timeout"))) }
        assertFalse(capturedLog.captured.success)
        assertEquals("timeout", capturedLog.captured.errorMessage)
    }

    @Test fun `logs binary response body as 'binary response only'`() {
        VendorApiLoggingInterceptor(settings, sink).intercept(chain(contentType = "application/octet-stream"))
        assertEquals(BINARY_BODY_PLACEHOLDER, capturedLog.captured.responseBody)
    }

    @Test fun `logs binary request body as 'binary response only'`() {
        VendorApiLoggingInterceptor(settings, sink).intercept(chain(requestContentType = "application/octet-stream"))
        assertEquals(BINARY_BODY_PLACEHOLDER, capturedLog.captured.requestBody)
    }

    @Test fun `saves raw unmasked headers`() {
        VendorApiLoggingInterceptor(settings, sink).intercept(
            chain(extraHeaders = mapOf("Authorization" to "Bearer secret"))
        )
        assertEquals(listOf("Bearer secret"), capturedLog.captured.requestHeaders["Authorization"])
    }

    @Test fun `does not log when no TraceableApi annotation`() {
        val mockSink = mockk<VendorApiLogSink>()
        val plainRequest = Request.Builder().url("https://example.com").build()
        val mockChain = mockk<Interceptor.Chain>()
        every { mockChain.request() } returns plainRequest
        val response = Response.Builder().request(plainRequest).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body("{}".toResponseBody()).build()
        every { mockChain.proceed(any()) } returns response

        VendorApiLoggingInterceptor(settings, mockSink).intercept(mockChain)
        verify(exactly = 0) { mockSink.save(any()) }
    }
}
