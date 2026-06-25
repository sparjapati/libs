package com.indexing.annotation

import com.indexing.config.EntityIndexingConfiguration
import org.springframework.context.annotation.Import

/**
 * Enables the entity-indexing library in the importing application.
 *
 * @param basePackages Root packages of the importing application. Only beans whose class
 *   resides under one of these packages will be intercepted for [@ReindexContext][ReindexContext]
 *   and [@ReindexId][ReindexId] scanning. Setting this prevents the library from intercepting
 *   Spring-internal and third-party library beans.
 *
 *   When left empty the library falls back to intercepting beans carrying a Spring stereotype
 *   annotation (`@Service`, `@Component`, `@Repository`, `@RestController`).
 *
 * Example:
 * ```kotlin
 * @SpringBootApplication
 * @EnableEntityIndexing(basePackages = ["com.myapp"])
 * class MyApplication
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(EntityIndexingConfiguration::class)
annotation class EnableEntityIndexing(
    val basePackages: Array<String> = [],
)
