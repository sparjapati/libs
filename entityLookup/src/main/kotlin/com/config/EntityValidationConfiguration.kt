package com.config

import com.annotation.EnableEntityValidation
import com.aspect.EntityValidationAspect
import com.service.EntityLookupRegistry
import com.service.EntityLookupService
import com.service.EntityValidationCache
import org.slf4j.LoggerFactory
import org.springframework.aop.aspectj.AspectJExpressionPointcut
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.ImportAware
import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.core.type.AnnotationMetadata
import org.springframework.web.context.WebApplicationContext

// Loaded exclusively via @Import from @EnableEntityValidation — no @ConditionalOnBean needed.
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class EntityValidationConfiguration : ImportAware {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(EntityValidationConfiguration::class.java)
    }

    private var basePackages: Array<String> = emptyArray()

    override fun setImportMetadata(importMetadata: AnnotationMetadata) {
        val attrs = importMetadata.getAnnotationAttributes(EnableEntityValidation::class.java.name)
        basePackages = when (val raw = attrs?.get("basePackages")) {
            is Array<*> -> raw.filterIsInstance<String>().toTypedArray()
            else        -> emptyArray()
        }
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    fun entityValidationCache(): EntityValidationCache = EntityValidationCache()

    @Bean
    fun entityLookupRegistry(servicesProvider: ObjectProvider<List<EntityLookupService>>): EntityLookupRegistry =
        EntityLookupRegistry(servicesProvider)

    @Bean
    fun entityValidationAdvisor(
        registry: EntityLookupRegistry,
        cache: EntityValidationCache,
    ): DefaultPointcutAdvisor {
        LOGGER.info("Entity-lookup enabled!")
        val advice = EntityValidationAspect(entityLookupRegistry = registry, entityValidationCache = cache)
        return DefaultPointcutAdvisor(buildPointcut(), advice)
    }

    // Intercepts only @RestController beans. When basePackages is set the pointcut is further
    // narrowed to within(pkg..*) to exclude any third-party RestController beans.
    private fun buildPointcut(): AspectJExpressionPointcut {
        val restControllerExpr = "@within(org.springframework.web.bind.annotation.RestController)"
        val expression = if (basePackages.isNotEmpty()) {
            "(${basePackages.joinToString(" || ") { "within($it..*)" }}) && $restControllerExpr"
        } else {
            restControllerExpr
        }
        return AspectJExpressionPointcut().apply { this.expression = expression }
    }
}
