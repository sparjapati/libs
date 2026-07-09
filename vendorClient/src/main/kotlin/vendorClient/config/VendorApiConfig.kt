package vendorClient.config

/**
 * Runtime configuration for a single vendor API endpoint, covering rate-limit parameters,
 * enabled state, optional temporary disable window, and resilience settings.
 */
data class VendorApiConfig(
    val apiName: String,
    val maxRequests: Int,
    val windowSeconds: Int,
    val enabled: Boolean,
    val tempDisabledUntil: Long?,
    val resilience: VendorApiResilienceConfig = VendorApiResilienceConfig(),
) {
    /** Returns true if [now] (epoch millis) falls before [tempDisabledUntil]. */
    fun isTemporarilyDisabled(now: Long): Boolean = tempDisabledUntil != null && tempDisabledUntil > now
}
