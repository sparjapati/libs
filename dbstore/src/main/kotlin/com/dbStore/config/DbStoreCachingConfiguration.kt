package com.dbStore.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.dbStore.aspect.DbStoreCacheEvictAspect
import com.dbStore.aspect.DbStoreCachePutAspect
import com.dbStore.aspect.DbStoreCacheableAspect
import com.dbStore.mysqlDbstore.repository.MysqlDbStoreCacheRepository
import com.dbStore.mysqlDbstore.service.MysqlDbStoreCacheService
import com.dbStore.service.DbStoreCacheSupport
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import com.dbStore.service.DbStoreService
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory

// Loaded exclusively via @Import from @EnableDbStoreCaching — no @ConditionalOnBean needed.
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class DbStoreCachingConfiguration {

    @Bean
    fun mysqlDbStoreCacheRepository(entityManager: EntityManager): MysqlDbStoreCacheRepository =
        JpaRepositoryFactory(entityManager).getRepository(MysqlDbStoreCacheRepository::class.java)

    @Bean
    fun dbStoreService(
        repository: MysqlDbStoreCacheRepository,
        objectMapper: ObjectMapper,
    ): DbStoreService = MysqlDbStoreCacheService(repository, objectMapper)

    @Bean
    fun dbStoreCacheSupport(
        dbStoreService: DbStoreService,
        objectMapper: ObjectMapper,
    ) = DbStoreCacheSupport(dbStoreService, objectMapper)

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
