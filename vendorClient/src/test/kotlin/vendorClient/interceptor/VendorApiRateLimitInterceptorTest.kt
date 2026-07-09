package vendorClient.interceptor

import vendorClient.VendorApiKey
import vendorClient.annotation.TraceableApi
import vendorClient.config.VendorApiConfig
import vendorClient.exception.VendorApiDisabledException
import vendorClient.exception.VendorApiRateLimitExceededException
import vendorClient.exception.VendorApiTemporarilyDisabledException
import vendorClient.ratelimit.RateLimitStore
import io.mockk.*
import okhttp3.*
import retrofit2.Invocation
import java.time.Instant
import kotlin.test.*

class VendorApiRateLimitInterceptorTest {

    enum class TestApi : VendorApiKey { MY_API }

    private fun chain(api: TestApi? = TestApi.MY_API, config: VendorApiConfig? = enabledConfig()): Interceptor.Chain {
        val method = if (api != null) {
            val m: java.lang.reflect.Method = mockk()
            every { m.getAnnotation(TraceableApi::class.java) } returns TraceableApi(TestApi::class, api.name)
            m
        } else {
            val m: java.lang.reflect.Method = mockk()
            every { m.getAnnotation(TraceableApi::class.java) } returns null
            m
        }
        val invocation: Invocation = mockk()
        every { invocation.method() } returns method

        val request = Request.Builder().url("https://example.com")
            .tag(Invocation::class.java, invocation).build()

        val chain: Interceptor.Chain = mockk()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns mockk(relaxed = true)
        return chain
    }

    private fun enabledConfig(
        tempDisabledUntil: Instant? = null,
        enabled: Boolean = true,
    ) = VendorApiConfig(
        apiName = "MY_API",
        maxRequests = 10, windowSeconds = 60,
        enabled = enabled, tempDisabledUntil = tempDisabledUntil,
    )

    private val onTempDisable: (VendorApiKey, Instant) -> Unit = mockk(relaxed = true)

    @Test fun `passes through when no annotation`() {
        val store: RateLimitStore = mockk()
        val interceptor = VendorApiRateLimitInterceptor(
            getConfig = { null },
            rateLimitStore = store,
            onTempDisable = onTempDisable,
        )
        interceptor.intercept(chain(api = null))
        verify(exactly = 0) { store.tryAcquire(any(), any(), any()) }
    }

    @Test fun `throws VendorApiDisabledException when API is disabled`() {
        val interceptor = VendorApiRateLimitInterceptor(
            getConfig = { enabledConfig(enabled = false) },
            rateLimitStore = mockk(),
            onTempDisable = onTempDisable,
        )
        assertFailsWith<VendorApiDisabledException> { interceptor.intercept(chain()) }
    }

    @Test fun `throws VendorApiTemporarilyDisabledException when API is in cooldown`() {
        val interceptor = VendorApiRateLimitInterceptor(
            getConfig = { enabledConfig(tempDisabledUntil = Instant.now().plusSeconds(60)) },
            rateLimitStore = mockk(),
            onTempDisable = onTempDisable,
        )
        assertFailsWith<VendorApiTemporarilyDisabledException> { interceptor.intercept(chain()) }
    }

    @Test fun `throws VendorApiRateLimitExceededException and calls onTempDisable`() {
        val store: RateLimitStore = mockk { every { tryAcquire(any(), any(), any()) } returns false }
        val interceptor = VendorApiRateLimitInterceptor(
            getConfig = { enabledConfig() },
            rateLimitStore = store,
            onTempDisable = onTempDisable,
        )
        assertFailsWith<VendorApiRateLimitExceededException> { interceptor.intercept(chain()) }
        verify(exactly = 1) { onTempDisable(TestApi.MY_API, any()) }
    }

    @Test fun `proceeds normally when token acquired`() {
        val store: RateLimitStore = mockk { every { tryAcquire(any(), any(), any()) } returns true }
        val c = chain()
        val interceptor = VendorApiRateLimitInterceptor(
            getConfig = { enabledConfig() },
            rateLimitStore = store,
            onTempDisable = onTempDisable,
        )
        interceptor.intercept(c)
        verify(exactly = 1) { c.proceed(any()) }
    }
}
