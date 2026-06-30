package vendorClient.jpa.autoconfigure

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import vendorClient.jpa.config.JpaVendorApiConfigManager
import vendorClient.jpa.config.JpaVendorApiConfigProvider
import vendorClient.jpa.logging.JpaVendorApiLogQuery
import vendorClient.jpa.logging.JpaVendorApiLogSink
import vendorClient.jpa.repository.VendorApiConfigRepository
import vendorClient.jpa.repository.VendorApiLogRepository

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = ["vendorClient.jpa.repository"])
@EntityScan(basePackages = ["vendorClient.jpa.entity"])
class VendorClientJpaAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    fun jpaVendorApiConfigProvider(repository: VendorApiConfigRepository): JpaVendorApiConfigProvider =
        JpaVendorApiConfigProvider(repository)

    @Bean @ConditionalOnMissingBean
    fun jpaVendorApiConfigManager(repository: VendorApiConfigRepository): JpaVendorApiConfigManager =
        JpaVendorApiConfigManager(repository)

    @Bean @ConditionalOnMissingBean
    fun jpaVendorApiLogSink(repository: VendorApiLogRepository): JpaVendorApiLogSink =
        JpaVendorApiLogSink(repository)

    @Bean @ConditionalOnMissingBean
    fun jpaVendorApiLogQuery(repository: VendorApiLogRepository): JpaVendorApiLogQuery =
        JpaVendorApiLogQuery(repository)
}
