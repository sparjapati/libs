package com.sparjapati.vendorClient.ratelimit

import com.sparjapati.vendorClient.VendorApiKey

/**
 * Backend port for the sliding-window rate limiter.
 *
 * Declared as a `fun interface` to allow lightweight lambda implementations in tests.
 * Production implementations live in adapter modules (e.g. `vendorClient-redis`).
 */
fun interface RateLimitStore {
    /**
     * Attempts to acquire one token for [api] within a sliding window of [windowSeconds].
     * Returns true if the token was granted; false if the limit is already reached.
     */
    fun tryAcquire(api: VendorApiKey, maxRequests: Int, windowSeconds: Int): Boolean
}
