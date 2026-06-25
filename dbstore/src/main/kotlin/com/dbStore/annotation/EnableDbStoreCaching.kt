package com.dbStore.annotation

import com.dbStore.config.DbStoreCachingConfiguration
import org.springframework.context.annotation.Import

/**
 * Enables the dbstore caching library in the importing application.
 *
 * @param basePackages Root packages of the importing application. Only beans whose class
 *   resides under one of these packages will be intercepted for [@DbStoreCacheable][DbStoreCacheable],
 *   [@DbStoreCachePut][DbStoreCachePut], and [@DbStoreCacheEvict][DbStoreCacheEvict] scanning.
 *   When left empty the pointcut falls back to the annotation predicate alone
 *   (`@annotation(...)`) which is already safe since only user code carries these annotations.
 *
 * Example:
 * ```kotlin
 * @SpringBootApplication
 * @EnableDbStoreCaching(basePackages = ["com.myapp"])
 * class MyApplication
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(DbStoreCachingConfiguration::class)
annotation class EnableDbStoreCaching(
    val basePackages: Array<String> = [],
)
