package com.sparjapati.dbStore.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.sparjapati.dbStore.aspect.DbStoreCacheEvictAspect
import com.sparjapati.dbStore.aspect.DbStoreCachePutAspect
import com.sparjapati.dbStore.aspect.DbStoreCacheableAspect
import com.sparjapati.dbStore.mysqlDbstore.repository.MysqlDbStoreCacheRepository
import com.sparjapati.dbStore.mysqlDbstore.service.MysqlDbStoreCacheService
import com.sparjapati.dbStore.service.DbStoreCacheSupport
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import com.sparjapati.dbStore.annotation.EnableDbStoreCaching
import com.sparjapati.dbStore.service.DbStoreService
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@ConditionalOnBean(annotation = [EnableDbStoreCaching::class])
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableJpaRepositories(
    basePackageClasses = [MysqlDbStoreCacheRepository::class]
)
class DbStoreCachingConfiguration {
    companion object {
        private val objectMapper = ObjectMapper().findAndRegisterModules()
    }

    @Bean
    fun dbStoreService(
        repository: MysqlDbStoreCacheRepository,
    ): DbStoreService = MysqlDbStoreCacheService(repository)

    @Bean
    fun dbStoreCacheSupport(dbStoreService: DbStoreService) = DbStoreCacheSupport(dbStoreService, objectMapper)

    @Bean
    fun dbStoreCacheableAspect(cacheSupport: DbStoreCacheSupport): DbStoreCacheableAspect {
        return DbStoreCacheableAspect(cacheSupport)
    }

    @Bean
    fun dbStoreCacheEvictAspect(cacheSupport: DbStoreCacheSupport): DbStoreCacheEvictAspect {
        return DbStoreCacheEvictAspect(cacheSupport)
    }

    @Bean
    fun dbStoreCachePutAspect(cacheSupport: DbStoreCacheSupport): DbStoreCachePutAspect {
        return DbStoreCachePutAspect(cacheSupport)
    }

}
