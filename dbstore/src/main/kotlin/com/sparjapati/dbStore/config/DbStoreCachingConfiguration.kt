package com.sparjapati.dbStore.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.sparjapati.dbStore.aspect.DbStoreCacheEvictAspect
import com.sparjapati.dbStore.aspect.DbStoreCachePutAspect
import com.sparjapati.dbStore.aspect.DbStoreCacheableAspect
import com.sparjapati.dbStore.mysqlDbstore.repository.MysqlDbStoreCacheRepository
import com.sparjapati.dbStore.mysqlDbstore.service.MysqlDbStoreCacheService
import com.sparjapati.dbStore.service.DbStoreCacheSupport
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import com.sparjapati.dbStore.service.DbStoreService
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
