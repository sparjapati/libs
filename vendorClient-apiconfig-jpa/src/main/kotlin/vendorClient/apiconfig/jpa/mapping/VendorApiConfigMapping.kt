package vendorClient.apiconfig.jpa.mapping

import vendorClient.config.VendorApiConfig
import vendorClient.config.VendorApiResilienceConfig
import vendorClient.apiconfig.jpa.entity.VendorApiConfigEntity

fun VendorApiConfigEntity.toDto(): VendorApiConfig = VendorApiConfig(
    maxRequests = maxRequests,
    windowSeconds = windowSeconds,
    enabled = enabled,
    tempDisabledUntil = tempDisabledUntil,
    resilience = VendorApiResilienceConfig(
        cbEnabled = cbEnabled,
        cbFailureRateThreshold = cbFailureRateThreshold ?: 50,
        cbWaitDurationSeconds = cbWaitDurationSeconds ?: 30,
        cbSlidingWindowSize = cbSlidingWindowSize ?: 10,
        retryEnabled = retryEnabled,
        retryMaxAttempts = retryMaxAttempts ?: 3,
        retryInitialIntervalMs = retryInitialIntervalMs ?: 500L,
        retryMultiplier = retryMultiplier ?: 2.0,
        retryMaxIntervalMs = retryMaxIntervalMs ?: 10_000L,
    ),
)

fun VendorApiConfig.toEntity(apiName: String): VendorApiConfigEntity = VendorApiConfigEntity(
    apiName = apiName,
    maxRequests = maxRequests,
    windowSeconds = windowSeconds,
    enabled = enabled,
    tempDisabledUntil = tempDisabledUntil,
    cbEnabled = resilience.cbEnabled,
    cbFailureRateThreshold = resilience.cbFailureRateThreshold,
    cbWaitDurationSeconds = resilience.cbWaitDurationSeconds,
    cbSlidingWindowSize = resilience.cbSlidingWindowSize,
    retryEnabled = resilience.retryEnabled,
    retryMaxAttempts = resilience.retryMaxAttempts,
    retryInitialIntervalMs = resilience.retryInitialIntervalMs,
    retryMultiplier = resilience.retryMultiplier,
    retryMaxIntervalMs = resilience.retryMaxIntervalMs,
)
