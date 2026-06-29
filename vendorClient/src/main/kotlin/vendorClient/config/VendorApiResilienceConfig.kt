package vendorClient.config

/**
 * Resilience configuration for a vendor API — controls circuit-breaker and retry behaviour.
 *
 * Both features are disabled by default so that callers opt-in explicitly rather than
 * inheriting unexpected retry or circuit-breaker behaviour.
 */
data class VendorApiResilienceConfig(
    val cbEnabled: Boolean = false,
    val cbFailureRateThreshold: Int = 50,
    val cbWaitDurationSeconds: Int = 30,
    val cbSlidingWindowSize: Int = 10,
    val retryEnabled: Boolean = false,
    val retryMaxAttempts: Int = 3,
    val retryInitialIntervalMs: Long = 500L,
    val retryMultiplier: Double = 2.0,
    val retryMaxIntervalMs: Long = 10_000L,
)
