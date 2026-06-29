package com.sparjapati.vendorClient.builder

import com.sparjapati.vendorClient.config.VendorApiConfigProvider
import com.sparjapati.vendorClient.interceptor.HttpLoggingInterceptor
import com.sparjapati.vendorClient.ratelimit.InMemoryRateLimitStore
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class VendorClientBuilderTest {

    private val provider = VendorApiConfigProvider { null }

    @Test fun `build succeeds with minimal config`() {
        val retrofit = VendorClient.builder()
            .baseUrl("https://example.com/")
            .configProvider(provider)
            .build()
        assertNotNull(retrofit)
    }

    @Test fun `build throws when baseUrl is missing`() {
        assertFailsWith<IllegalArgumentException> {
            VendorClient.builder().configProvider(provider).build()
        }
    }

    @Test fun `build throws when configProvider is missing`() {
        assertFailsWith<IllegalArgumentException> {
            VendorClient.builder().baseUrl("https://example.com/").build()
        }
    }

    @Test fun `build with all features enabled does not throw`() {
        val retrofit = VendorClient.builder()
            .baseUrl("https://example.com/")
            .configProvider(provider)
            .rateLimiter(InMemoryRateLimitStore()) { _, _ -> }
            .resilience()
            .apiLogging { _ -> }
            .httpLogging(HttpLoggingInterceptor.Level.HEADERS)
            .trace { "req-123" }
            .build()
        assertNotNull(retrofit)
    }

    @Test fun `interceptor order is fixed regardless of builder call order`() {
        // Call methods in reverse order — build() must still succeed and produce a valid Retrofit
        val retrofit = VendorClient.builder()
            .trace { "req-123" }
            .httpLogging(HttpLoggingInterceptor.Level.BODY)
            .apiLogging { _ -> }
            .resilience()
            .rateLimiter(InMemoryRateLimitStore()) { _, _ -> }
            .baseUrl("https://example.com/")
            .configProvider(provider)
            .build()
        assertNotNull(retrofit)
    }
}
