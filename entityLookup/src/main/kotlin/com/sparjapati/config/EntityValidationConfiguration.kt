package com.sparjapati.config

import com.sparjapati.annotation.EnableEntityValidation
import com.sparjapati.service.EntityLookupRegistry
import com.sparjapati.aspect.EntityValidationAspect
import com.sparjapati.service.EntityLookupService
import com.sparjapati.service.EntityValidationCache
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.web.context.WebApplicationContext

@Configuration
@ConditionalOnBean(annotation = [EnableEntityValidation::class])
@EnableAspectJAutoProxy(proxyTargetClass = true)
class EntityValidationConfiguration {
    companion object {
        val LOGGER = LoggerFactory.getLogger(EntityValidationConfiguration::class.java)
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    fun entityValidationCache(): EntityValidationCache {
        return EntityValidationCache()
    }

    @Bean
    fun entityLookupRegistry(services: List<EntityLookupService>): EntityLookupRegistry {
        return EntityLookupRegistry(services)
    }

    @Bean
    fun entityValidationAspect(
        registry: EntityLookupRegistry,
        cache: EntityValidationCache
    ): EntityValidationAspect {
        LOGGER.info("Entity-lookup enabled!")
        return EntityValidationAspect(
            entityLookupRegistry = registry,
            entityValidationCache = cache
        )
    }
}
