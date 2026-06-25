package com.annotation

import com.config.EntityValidationConfiguration
import org.springframework.context.annotation.Import

/**
 * Enables the entity-validation library in the importing application.
 *
 * @param basePackages Root packages of the importing application. When set, only
 *   `@RestController` beans whose class resides under one of these packages will be
 *   intercepted for `@Entity` parameter validation. When left empty the pointcut matches
 *   all `@RestController` beans (the existing behaviour).
 *
 * Example:
 * ```kotlin
 * @SpringBootApplication
 * @EnableEntityValidation(basePackages = ["com.myapp"])
 * class MyApplication
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(EntityValidationConfiguration::class)
annotation class EnableEntityValidation(
    val basePackages: Array<String> = [],
)
