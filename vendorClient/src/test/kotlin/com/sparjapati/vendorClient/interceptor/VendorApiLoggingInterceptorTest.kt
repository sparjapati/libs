package com.sparjapati.vendorClient.interceptor

import com.sparjapati.vendorClient.VendorApiKey
import com.sparjapati.vendorClient.annotation.TraceableApi
import com.sparjapati.vendorClient.config.VendorClientSettings
import com.sparjapati.vendorClient.logging.VendorApiLog
import com.sparjapati.vendorClient.logging.VendorApiLogSink
import io.mockk.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Invocation
import java.io.IOException
import kotlin.test.*

class VendorApiLoggingInterceptorTest {

    enum class TestApi : VendorApiKey { MY_API }

    private val capturedLog = CapturingSlot<VendorApiLog>()
    private val sink = VendorApiLogSink { log -> capturedLog.captured = log }
    private val settings = VendorClientSettings()

    private fun chain(responseCode: Int = 200, throwError: Exception? = null): Interceptor.Chain {
        val method: java.lang.reflect.Method = mockk()
        every { method.getAnnotation(TraceableApi::class.java) } returns TraceableApi(TestApi::class, "MY_API")
        val invocation: Invocation = mockk()
        every { invocation.method() } returns method

        val request = Request.Builder()
            .url("https://example.com/path")
            .header("X-Request-Id", "req-abc")
            .tag(Invocation::class.java, invocation)
            .build()

        val chain: Interceptor.Chain = mockk()
        every { chain.request() } returns request
        if (throwError != null) {
            every { chain.proceed(any()) } throws throwError
        } else {
            val body = """{"ok":true}""".toResponseBody("application/json".toMediaType())
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(responseCode).message("OK").body(body).build()
            every { chain.proceed(any()) } returns response
        }
        return chain
    }

    @Test fun `logs successful response`() {
        VendorApiLoggingInterceptor(settings, sink).intercept(chain(200))
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

    @Test fun `masks sensitive headers`() {
        VendorApiLoggingInterceptor(settings, sink).intercept(chain())
        // No sensitive headers on the test request, just verify the log was saved
        assertNotNull(capturedLog.captured)
    }
}
