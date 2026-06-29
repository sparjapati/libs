package vendorClient.autoconfigure

import vendorClient.config.DEFAULT_SENSITIVE_HEADERS
import vendorClient.config.VendorClientSettings
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VendorClientSettingsProperties::class)
class VendorClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun vendorClientSettings(props: VendorClientSettingsProperties): VendorClientSettings =
        VendorClientSettings(
            requestIdHeader = props.requestIdHeader,
            rateLimiterKeyPrefix = props.rateLimiterKeyPrefix,
            sensitiveHeaders = props.sensitiveHeaders ?: DEFAULT_SENSITIVE_HEADERS,
            connectTimeoutSeconds = props.connectTimeoutSeconds,
            readTimeoutSeconds = props.readTimeoutSeconds,
            writeTimeoutSeconds = props.writeTimeoutSeconds,
        )
}
