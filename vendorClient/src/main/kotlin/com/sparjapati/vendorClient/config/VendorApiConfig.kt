package com.sparjapati.vendorClient.config

import java.time.Instant

/**
 * Runtime configuration for a single vendor API endpoint, covering rate-limit parameters,
 * enabled state, optional temporary disable window, and resilience settings.
 */
data class VendorApiConfig(
    val maxRequests: Int,
    val windowSeconds: Int,
    val enabled: Boolean,
    val tempDisabledUntil: Instant?,
    val resilience: VendorApiResilienceConfig = VendorApiResilienceConfig(),
) {
    /** Returns true if [now] falls before the [tempDisabledUntil] instant. */
    fun isTemporarilyDisabled(now: Instant): Boolean = tempDisabledUntil?.isAfter(now) == true
}
