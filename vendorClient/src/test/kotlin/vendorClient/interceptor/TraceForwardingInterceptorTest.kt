package vendorClient.interceptor

import vendorClient.VendorApiKey
import vendorClient.annotation.TraceableApi
import vendorClient.config.VendorClientSettings
import io.mockk.*
import okhttp3.*
import retrofit2.Invocation
import kotlin.test.*

class TraceForwardingInterceptorTest {

    enum class TestApi : VendorApiKey {
        DEFAULT_HEADER,
        CUSTOM_HEADER { override val traceHeader = "X-Custom-Trace" },
    }

    private fun settings() = VendorClientSettings(requestIdHeader = "X-Request-Id")

    private fun chain(api: TestApi, capturedRequest: CapturingSlot<Request> = slot()): Interceptor.Chain {
        val method: java.lang.reflect.Method = mockk()
        every { method.getAnnotation(TraceableApi::class.java) } returns TraceableApi(TestApi::class, api.name)
        val invocation: Invocation = mockk()
        every { invocation.method() } returns method
        val request = Request.Builder().url("https://example.com")
            .tag(Invocation::class.java, invocation).build()
        val chain: Interceptor.Chain = mockk()
        every { chain.request() } returns request
        every { chain.proceed(capture(capturedRequest)) } returns mockk(relaxed = true)
        return chain
    }

    @Test fun `does not add header when requestIdProvider returns null`() {
        val interceptor = TraceForwardingInterceptor(settings()) { null }
        val slot = CapturingSlot<Request>()
        val chain = chain(TestApi.DEFAULT_HEADER, slot)
        interceptor.intercept(chain)
        assertNull(slot.captured.header("X-Request-Id"))
    }

    @Test fun `uses global requestIdHeader by default`() {
        val interceptor = TraceForwardingInterceptor(settings()) { "req-123" }
        val slot = CapturingSlot<Request>()
        interceptor.intercept(chain(TestApi.DEFAULT_HEADER, slot))
        val header = slot.captured.header("X-Request-Id")
        assertNotNull(header)
        assertTrue(header.startsWith("req-123-"))
    }

    @Test fun `uses per-API traceHeader when defined`() {
        val interceptor = TraceForwardingInterceptor(settings()) { "req-123" }
        val slot = CapturingSlot<Request>()
        interceptor.intercept(chain(TestApi.CUSTOM_HEADER, slot))
        assertNotNull(slot.captured.header("X-Custom-Trace"))
        assertNull(slot.captured.header("X-Request-Id"))
    }

    @Test fun `attempt id has format {requestId}-{8chars}`() {
        val interceptor = TraceForwardingInterceptor(settings()) { "abc" }
        val slot = CapturingSlot<Request>()
        interceptor.intercept(chain(TestApi.DEFAULT_HEADER, slot))
        val header = slot.captured.header("X-Request-Id")!!
        assertTrue(header.matches(Regex("abc-[a-f0-9\\-]{8}")))
    }
}
