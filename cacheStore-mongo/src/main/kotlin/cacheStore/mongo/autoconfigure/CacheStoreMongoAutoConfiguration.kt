package cacheStore.mongo.autoconfigure

import cacheStore.CacheStore
import cacheStore.mongo.MongoCacheStore
import cacheStore.mongo.repository.CacheStoreEntryRepository
import cacheStore.spring.StoreBackedCacheManager
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

// Name-scoped @ConditionalOnMissingBean, same reasoning as cacheStore-mysql's autoconfiguration:
// a type-scoped check on a CacheManager-returning method would see the host app's existing
// @Primary CacheManager bean and silently skip registering "mongoCacheManager".
@Configuration(proxyBeanMethods = false)
@EnableMongoRepositories(basePackages = ["cacheStore.mongo.repository"])
@EnableConfigurationProperties(CacheStoreMongoProperties::class)
class CacheStoreMongoAutoConfiguration {

    @Bean(CacheStoreMongoBeanNames.CACHE_STORE)
    @ConditionalOnMissingBean(name = [CacheStoreMongoBeanNames.CACHE_STORE])
    fun mongoCacheStore(repository: CacheStoreEntryRepository): MongoCacheStore =
        MongoCacheStore(repository)

    @Bean(CacheStoreMongoBeanNames.CACHE_MANAGER)
    @ConditionalOnMissingBean(name = [CacheStoreMongoBeanNames.CACHE_MANAGER])
    fun mongoCacheManager(
        @Qualifier(CacheStoreMongoBeanNames.CACHE_STORE) mongoCacheStore: CacheStore,
        objectMapper: ObjectMapper,
        props: CacheStoreMongoProperties,
    ): CacheManager =
        StoreBackedCacheManager(cacheStore = mongoCacheStore, objectMapper = objectMapper, defaultTtl = props.defaultTtl)
}
