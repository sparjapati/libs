package com.idempotency

import com.idempotency.config.IdempotencyConfiguration
import org.springframework.context.annotation.Import

/**
 * Enables the idempotency library in the importing application.
 *
 * @param basePackages Root packages of the importing application. Only beans whose class
 *   resides under one of these packages will be intercepted for [@Idempotent][Idempotent]
 *   scanning. When left empty the pointcut falls back to the annotation predicate alone.
 *
 * Example:
 * ```kotlin
 * @SpringBootApplication
 * @EnableIdempotency(basePackages = ["com.myapp"])
 * class MyApplication
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(IdempotencyConfiguration::class)
annotation class EnableIdempotency(
    val basePackages: Array<String> = [],
)
