package vendorClient.interceptor

import vendorClient.VendorApiKey
import vendorClient.annotation.TraceableApi
import vendorClient.config.VendorApiConfig
import vendorClient.config.VendorApiResilienceConfig
import vendorClient.exception.VendorApiCircuitOpenException
import io.mockk.*
import okhttp3.*
import retrofit2.Invocation
import java.io.IOException
import kotlin.test.*

class VendorApiResilienceInterceptorTest {

    enum class TestApi : VendorApiKey { MY_API }

    private fun config(resilience: VendorApiResilienceConfig) = VendorApiConfig(
        maxRequests = 10, windowSeconds = 60, enabled = true,
        tempDisabledUntil = null, resilience = resilience,
    )

    private fun chain(respondWith: () -> Response): Interceptor.Chain {
        val method: java.lang.reflect.Method = mockk()
        every { method.getAnnotation(TraceableApi::class.java) } returns TraceableApi(TestApi::class, "MY_API")
        val invocation: Invocation = mockk()
        every { invocation.method() } returns method
        val request = Request.Builder().url("https://example.com")
            .tag(Invocation::class.java, invocation).build()
        val chain: Interceptor.Chain = mockk()
        every { chain.request() } returns request
        every { chain.proceed(any()) } answers { respondWith() }
        return chain
    }

    private fun successResponse() = mockk<Response>(relaxed = true) {
        every { code } returns 200
    }

    private fun errorResponse() = mockk<Response>(relaxed = true) {
        every { code } returns 500
        every { close() } just Runs
    }

    @Test fun `passes through when both disabled`() {
        val interceptor = VendorApiResilienceInterceptor(
            getConfig = { config(VendorApiResilienceConfig(cbEnabled = false, retryEnabled = false)) }
        )
        val chain = chain { successResponse() }
        interceptor.intercept(chain)
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test fun `retry calls chain N times on IOException`() {
        val interceptor = VendorApiResilienceInterceptor(
            getConfig = {
                config(VendorApiResilienceConfig(
                    retryEnabled = true, retryMaxAttempts = 3,
                    retryInitialIntervalMs = 1, retryMultiplier = 1.0, retryMaxIntervalMs = 1,
                ))
            }
        )
        val chain = chain { throw IOException("network error") }
        assertFailsWith<IOException> { interceptor.intercept(chain) }
        verify(exactly = 3) { chain.proceed(any()) }
    }

    @Test fun `circuit breaker opens after failure threshold`() {
        val interceptor = VendorApiResilienceInterceptor(
            getConfig = {
                config(VendorApiResilienceConfig(
                    cbEnabled = true, cbSlidingWindowSize = 4,
                    cbFailureRateThreshold = 100, cbWaitDurationSeconds = 60,
                    retryEnabled = false,
                ))
            }
        )
        val chain = chain { throw IOException("fail") }
        // Fail 4 times to fill the window at 100% failure rate — CB opens
        repeat(4) { runCatching { interceptor.intercept(chain) } }
        // Next call should throw VendorApiCircuitOpenException without hitting chain.proceed
        assertFailsWith<VendorApiCircuitOpenException> { interceptor.intercept(chain) }
    }

    @Test fun `5xx response is treated as failure for retry`() {
        var attempts = 0
        val interceptor = VendorApiResilienceInterceptor(
            getConfig = {
                config(VendorApiResilienceConfig(
                    retryEnabled = true, retryMaxAttempts = 2,
                    retryInitialIntervalMs = 1, retryMultiplier = 1.0, retryMaxIntervalMs = 1,
                ))
            }
        )
        val chain = chain { attempts++; errorResponse() }
        assertFailsWith<IOException> { interceptor.intercept(chain) }
        assertEquals(2, attempts)
    }
}
