package com.sparjapati.config

import com.sparjapati.service.EntityLookupRegistry
import com.sparjapati.aspect.EntityValidationAspect
import com.sparjapati.service.EntityLookupService
import com.sparjapati.service.EntityValidationCache
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(basePackages = ["com.sparjapati"])
class EntityValidationConfiguration {
    companion object {
        val LOGGER = LoggerFactory.getLogger(EntityValidationConfiguration::class.java)
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
