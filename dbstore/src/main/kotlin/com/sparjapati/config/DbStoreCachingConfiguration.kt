package com.sparjapati.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.sparjapati.aspect.DbStoreAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import com.sparjapati.service.DbStoreService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(
    basePackages = ["com.sparjapati.mysqlDbstore"]
)
@EnableJpaRepositories(
    basePackages = ["com.sparjapati.mysqlDbstore.repository"]
)
class DbStoreCachingConfiguration {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DbStoreCachingConfiguration::class.java)
        private val objectMapper = ObjectMapper().findAndRegisterModules()
    }

    @PostConstruct
    fun logInitialization() {
        LOGGER.info("✅ DbStore caching has been configured and enabled")
    }

    @Bean
    fun dbStoreAspect(dbStoreService: DbStoreService): DbStoreAspect {
        return DbStoreAspect(dbStoreService, objectMapper)
    }
}
