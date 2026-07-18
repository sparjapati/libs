package com.bulkFileProcessing.jobstore

import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

// Ordered dead last (Ordered.LOWEST_PRECEDENCE) among all auto-configurations so that any store
// adapter's own BulkJobStore bean (e.g. bulkFileProcessing-mysql, registered at normal
// auto-configuration precedence) is always seen first by @ConditionalOnMissingBean here,
// regardless of which adapter it is. Without this, these fallback beans would register before
// any adapter and silently win instead of yielding to it.
@Configuration(proxyBeanMethods = false)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
class BulkJobStoreDefaultsAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "bulk.job-store", name = ["type"], havingValue = "in-memory")
    @ConditionalOnMissingBean(BulkJobStore::class)
    fun inMemoryBulkJobStore(): BulkJobStore = InMemoryBulkJobStore()

    @Bean
    @ConditionalOnMissingBean(BulkJobStore::class)
    fun noOpBulkJobStore(): BulkJobStore = NoOpBulkJobStore()
}
