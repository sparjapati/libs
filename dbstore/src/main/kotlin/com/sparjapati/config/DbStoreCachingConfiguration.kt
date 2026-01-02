package com.sparjapati.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.sparjapati.aspect.DbStoreAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import com.sparjapati.service.DbStoreService
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManagerFactory
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import javax.sql.DataSource

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(
    basePackages = ["com.sparjapati.mysqlDbstore"]
)
@EnableJpaRepositories(
    basePackages = ["com.sparjapati.mysqlDbstore.repository"]
)
class DbStoreCachingConfiguration(
    private val applicationContext: ApplicationContext
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DbStoreCachingConfiguration::class.java)
        private val objectMapper = ObjectMapper().findAndRegisterModules()
    }

    @PostConstruct
    fun validateAndInitialize() {
        LOGGER.info("🔍 Validating DbStore caching prerequisites...")

        // 1️⃣ DbStoreService must exist
        val dbStoreService =
            applicationContext.getBeanProvider(DbStoreService::class.java)
                .ifAvailable
                ?: fail("DbStoreService bean not found")

        // 2️⃣ DataSource must exist (MySQL or compatible)
        applicationContext.getBeanProvider(DataSource::class.java)
            .ifAvailable
            ?: fail("No DataSource found. MySQL must be configured")

        // 3️⃣ EntityManagerFactory must exist
        val emf =
            applicationContext.getBeanProvider(EntityManagerFactory::class.java)
                .ifAvailable
                ?: fail("JPA EntityManagerFactory not found")

        // 4️⃣ Entity must be managed
        val isManaged = try {
            emf.metamodel.entities.any {
                it.javaType.name ==
                        "com.sparjapati.mysqlDbstore.entity.DbStoreCacheEntryEntity"
            }
        } catch (ex: Exception) {
            false
        }

        if (!isManaged) {
            fail("DbStoreCacheEntryEntity is not a managed JPA entity. Did you forget @EntityScan?")
        }

        // ✅ All checks passed
        LOGGER.info("✅ DbStore caching has been successfully configured")
    }

    private fun fail(message: String): Nothing {
        LOGGER.error("❌ DbStore caching configuration failed: {}", message)
        throw IllegalStateException(message)
    }

    @Bean
    fun dbStoreAspect(dbStoreService: DbStoreService): DbStoreAspect {
        return DbStoreAspect(dbStoreService, objectMapper)
    }
}
