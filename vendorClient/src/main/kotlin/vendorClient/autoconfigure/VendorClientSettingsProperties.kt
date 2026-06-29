package vendorClient.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "vendor-client")
data class VendorClientSettingsProperties(
    val requestIdHeader: String = "X-Request-Id",
    val rateLimiterKeyPrefix: String = "vendorApiRate",
    val sensitiveHeaders: Set<String>? = null,
    val connectTimeoutSeconds: Long = 30,
    val readTimeoutSeconds: Long = 30,
    val writeTimeoutSeconds: Long = 30,
)
