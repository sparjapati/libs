package vendorClient.ratelimit

import vendorClient.VendorApiKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Sliding-window rate limiter backed by an in-memory [ConcurrentHashMap].
 *
 * NOT suitable for multi-instance deployments — each JVM maintains an independent window,
 * effectively multiplying the effective rate limit by the instance count.
 * Use RedisRateLimitStore in production.
 */
class InMemoryRateLimitStore : RateLimitStore {

    private val windows = ConcurrentHashMap<String, ArrayDeque<Long>>()

    override fun tryAcquire(api: VendorApiKey, maxRequests: Int, windowSeconds: Int): Boolean {
        val now = System.currentTimeMillis()
        val windowMs = windowSeconds * 1_000L
        val window = windows.getOrPut(api.name) { ArrayDeque() }

        synchronized(window) {
            val cutoff = now - windowMs
            // Use <= so that entries exactly at the cutoff boundary are also evicted.
            // This ensures windowSeconds=0 works correctly: cutoff==now, so all prior
            // entries (including those added in the same millisecond) are expired.
            while (window.isNotEmpty() && window.first() <= cutoff) window.removeFirst()
            if (window.size >= maxRequests) return false
            window.addLast(now)
            return true
        }
    }
}
