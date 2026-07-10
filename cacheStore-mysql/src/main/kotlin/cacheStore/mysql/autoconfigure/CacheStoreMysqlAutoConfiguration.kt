package cacheStore.mysql.autoconfigure

import cacheStore.CacheStore
import cacheStore.mysql.JpaCacheStore
import cacheStore.mysql.repository.CacheStoreEntryRepository
import cacheStore.spring.StoreBackedCacheManager
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

// Bean methods below use name-scoped @ConditionalOnMissingBean, not the type-inferred default:
// a type-scoped check on a CacheManager-returning method would see the host app's existing
// @Primary CacheManager bean (e.g. caffeineCacheManager) and silently skip registering
// "mysqlCacheManager" altogether.
//
// @AutoConfigureAfter the host app's own default JPA repository scan (Boot 4:
// DataJpaRepositoriesAutoConfiguration) is required: that auto-configuration is itself
// @ConditionalOnMissingBean(JpaRepositoryFactoryBean.class) — if THIS class's own
// @EnableJpaRepositories registers its repository (which IS JpaRepositoryFactoryBean-backed)
// before the host's default scan runs, the host's own repositories (e.g. UserRepository) never
// get auto-discovered at all. Referenced by name (not by class) since spring-boot-data-jpa isn't
// a compile dependency of this module — only the host app pulls it in transitively.
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(name = ["org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"])
@EnableJpaRepositories(basePackages = ["cacheStore.mysql.repository"])
@EntityScan(basePackages = ["cacheStore.mysql.entity"])
@EnableConfigurationProperties(CacheStoreMysqlProperties::class)
class CacheStoreMysqlAutoConfiguration {

    @Bean(CacheStoreMysqlBeanNames.CACHE_STORE)
    @ConditionalOnMissingBean(name = [CacheStoreMysqlBeanNames.CACHE_STORE])
    fun mysqlCacheStore(repository: CacheStoreEntryRepository): JpaCacheStore =
        JpaCacheStore(repository)

    @Bean(CacheStoreMysqlBeanNames.CACHE_MANAGER)
    @ConditionalOnMissingBean(name = [CacheStoreMysqlBeanNames.CACHE_MANAGER])
    fun mysqlCacheManager(
        @Qualifier(CacheStoreMysqlBeanNames.CACHE_STORE) mysqlCacheStore: CacheStore,
        objectMapper: ObjectMapper,
        props: CacheStoreMysqlProperties,
    ): CacheManager =
        StoreBackedCacheManager(cacheStore = mysqlCacheStore, objectMapper = objectMapper, defaultTtl = props.defaultTtl)
}
