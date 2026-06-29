package vendorClient.ratelimit

import vendorClient.VendorApiKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryRateLimitStoreTest {

    private enum class TestApi : VendorApiKey { A, B }
    private val store = InMemoryRateLimitStore()

    @Test fun `allows requests up to the limit`() {
        repeat(5) { assertTrue(store.tryAcquire(TestApi.A, maxRequests = 5, windowSeconds = 60)) }
    }

    @Test fun `blocks the (limit+1)th request`() {
        repeat(3) { store.tryAcquire(TestApi.A, maxRequests = 3, windowSeconds = 60) }
        assertFalse(store.tryAcquire(TestApi.A, maxRequests = 3, windowSeconds = 60))
    }

    @Test fun `different APIs have independent windows`() {
        repeat(3) { store.tryAcquire(TestApi.A, maxRequests = 3, windowSeconds = 60) }
        assertTrue(store.tryAcquire(TestApi.B, maxRequests = 3, windowSeconds = 60))
    }

    @Test fun `expired entries are evicted and window refills`() {
        // Fill the window
        repeat(2) { store.tryAcquire(TestApi.A, maxRequests = 2, windowSeconds = 0) }
        // windowSeconds=0 means window is 0ms wide — all prior entries are already expired,
        // so next call should succeed even though we previously filled the limit.
        assertTrue(store.tryAcquire(TestApi.A, maxRequests = 2, windowSeconds = 0))
    }
}
