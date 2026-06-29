package com.sparjapati.vendorClient.config

/**
 * Global settings shared across all vendor API clients built with this library.
 *
 * Provide a single [VendorClientSettings] bean; individual [VendorApiKey] implementations
 * may override [VendorApiKey.traceHeader] to deviate from [requestIdHeader] per API.
 */
data class VendorClientSettings(
    /** Header name forwarded to vendor APIs carrying the trace/request ID. */
    val requestIdHeader: String = "X-Request-Id",
    /** Key prefix for rate-limiter backend (e.g. Redis sorted-set keys). */
    val rateLimiterKeyPrefix: String = "vendorApiRate",
    /** Headers whose values are replaced with "***" in logs. */
    val sensitiveHeaders: Set<String> = DEFAULT_SENSITIVE_HEADERS,
    /** Maximum bytes of request/response body captured in [com.sparjapati.vendorClient.logging.VendorApiLog]. */
    val apiLogMaxBodyBytes: Long = 32 * 1024,
    /** Maximum bytes printed by the HTTP logging interceptor. */
    val httpLogMaxBodyBytes: Long = 1_000,
    val connectTimeoutSeconds: Long = 30,
    val readTimeoutSeconds: Long = 30,
    val writeTimeoutSeconds: Long = 30,
)

val DEFAULT_SENSITIVE_HEADERS: Set<String> = setOf(
    "authorization", "x-api-key", "api-key",
    "proxy-authorization", "cookie", "set-cookie",
    "x-auth-token", "x-access-token", "token",
)
