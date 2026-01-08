package com.sparjapati.dbStore.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.sparjapati.dbStore.aspect.DbStoreAspect
import com.sparjapati.dbStore.mysqlDbstore.repository.MysqlDbStoreCacheRepository
import com.sparjapati.dbStore.mysqlDbstore.service.MysqlDbStoreCacheService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import com.sparjapati.dbStore.service.DbStoreService
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
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
        repository: MysqlDbStoreCacheRepository
    ): DbStoreService {
        return MysqlDbStoreCacheService(repository)
    }

    @Bean
    fun dbStoreAspect(dbStoreService: DbStoreService): DbStoreAspect {
        return DbStoreAspect(dbStoreService, objectMapper)
    }

}
