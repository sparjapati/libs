package com.idempotency.config

import com.idempotency.EnableIdempotency
import com.idempotency.IdempotencyKeyIssuer
import com.idempotency.IdempotencyProperties
import com.idempotency.IdempotencyStore
import com.idempotency.aspect.IdempotentAspect
import com.idempotency.support.IdempotencySupport
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.aop.aspectj.AspectJExpressionPointcut
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.core.type.AnnotationMetadata

@EnableIdempotency(basePackages = ["com.myapp"])
private class AnnotatedWithBasePackages

class IdempotencyConfigurationTest {

    private val store: IdempotencyStore = mockk()
    private val props = IdempotencyProperties()
    private val support = IdempotencySupport(jacksonObjectMapper())

    @Test fun `idempotencyKeyIssuer bean is created`() {
        val config = IdempotencyConfiguration()
        val issuer: IdempotencyKeyIssuer = config.idempotencyKeyIssuer(store, props)
        assertNotNull(issuer)
    }

    @Test fun `idempotentAdvisor wraps an IdempotentAspect built from the given beans`() {
        val config = IdempotencyConfiguration()
        val advisor = config.idempotentAdvisor(store, props, support)
        assertTrue(advisor.advice is IdempotentAspect)
    }

    @Test fun `pointcut matches only the annotation when no basePackages are set`() {
        val config = IdempotencyConfiguration()
        val advisor = config.idempotentAdvisor(store, props, support)
        val expression = (advisor.pointcut as AspectJExpressionPointcut).expression
        assertEquals("@annotation(com.idempotency.Idempotent)", expression)
    }

    @Test fun `pointcut is narrowed by basePackages from EnableIdempotency's import metadata`() {
        val metadata: AnnotationMetadata = AnnotationMetadata.introspect(AnnotatedWithBasePackages::class.java)
        val config = IdempotencyConfiguration()
        config.setImportMetadata(metadata)

        val advisor = config.idempotentAdvisor(store, props, support)
        val expression = (advisor.pointcut as AspectJExpressionPointcut).expression

        assertEquals("(within(com.myapp..*)) && @annotation(com.idempotency.Idempotent)", expression)
    }
}
