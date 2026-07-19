package com.idempotency.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.idempotency.EnableIdempotency
import com.idempotency.Idempotent
import com.idempotency.IdempotencyProperties
import com.idempotency.IdempotencyStore
import com.idempotency.aspect.IdempotentAspect
import com.idempotency.support.IdempotencySupport
import org.springframework.aop.aspectj.AspectJExpressionPointcut
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.ImportAware
import org.springframework.core.type.AnnotationMetadata

// Loaded exclusively via @Import from @EnableIdempotency.
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties(IdempotencyProperties::class)
class IdempotencyConfiguration : ImportAware {

    private var basePackages: Array<String> = emptyArray()

    override fun setImportMetadata(importMetadata: AnnotationMetadata) {
        val attrs = importMetadata.getAnnotationAttributes(EnableIdempotency::class.java.name)
        basePackages = when (val raw = attrs?.get("basePackages")) {
            is Array<*> -> raw.filterIsInstance<String>().toTypedArray()
            else -> emptyArray()
        }
    }

    @Bean
    fun idempotencySupport(objectMapper: ObjectMapper): IdempotencySupport = IdempotencySupport(objectMapper)

    @Bean
    fun idempotentAdvisor(
        store: IdempotencyStore,
        props: IdempotencyProperties,
        support: IdempotencySupport,
    ): DefaultPointcutAdvisor =
        DefaultPointcutAdvisor(buildPointcut(), IdempotentAspect(store = store, props = props, support = support))

    private fun buildPointcut(): AspectJExpressionPointcut {
        val annotationExpr = "@annotation(${Idempotent::class.java.name})"
        val expression = if (basePackages.isNotEmpty()) {
            "(${basePackages.joinToString(" || ") { "within($it..*)" }}) && $annotationExpr"
        } else {
            annotationExpr
        }
        return AspectJExpressionPointcut().apply { this.expression = expression }
    }
}
