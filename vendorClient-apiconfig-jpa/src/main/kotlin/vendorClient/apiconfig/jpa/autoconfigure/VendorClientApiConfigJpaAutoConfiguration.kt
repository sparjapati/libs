package vendorClient.apiconfig.jpa.autoconfigure

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import vendorClient.apiconfig.jpa.config.JpaVendorApiConfigManager
import vendorClient.apiconfig.jpa.config.JpaVendorApiConfigProvider
import vendorClient.apiconfig.jpa.repository.VendorApiConfigRepository

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = ["vendorClient.apiconfig.jpa.repository"])
@EntityScan(basePackages = ["vendorClient.apiconfig.jpa.entity"])
class VendorClientApiConfigJpaAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    fun jpaVendorApiConfigProvider(repository: VendorApiConfigRepository): JpaVendorApiConfigProvider =
        JpaVendorApiConfigProvider(repository)

    @Bean @ConditionalOnMissingBean
    fun jpaVendorApiConfigManager(repository: VendorApiConfigRepository): JpaVendorApiConfigManager =
        JpaVendorApiConfigManager(repository)
}
