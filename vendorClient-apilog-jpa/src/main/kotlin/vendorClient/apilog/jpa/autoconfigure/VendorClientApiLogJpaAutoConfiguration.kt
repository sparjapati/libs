package vendorClient.apilog.jpa.autoconfigure

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import vendorClient.apilog.jpa.logging.JpaVendorApiLogQuery
import vendorClient.apilog.jpa.logging.JpaVendorApiLogSink
import vendorClient.apilog.jpa.repository.VendorApiLogRepository

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = ["vendorClient.apilog.jpa.repository"])
@EntityScan(basePackages = ["vendorClient.apilog.jpa.entity"])
class VendorClientApiLogJpaAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    fun jpaVendorApiLogSink(repository: VendorApiLogRepository): JpaVendorApiLogSink =
        JpaVendorApiLogSink(repository)

    @Bean @ConditionalOnMissingBean
    fun jpaVendorApiLogQuery(repository: VendorApiLogRepository): JpaVendorApiLogQuery =
        JpaVendorApiLogQuery(repository)
}
