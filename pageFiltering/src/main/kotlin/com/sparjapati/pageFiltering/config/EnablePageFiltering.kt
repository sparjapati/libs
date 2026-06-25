package com.sparjapati.pageFiltering.config

import org.springframework.context.annotation.Import

/**
 * Add to a [@SpringBootApplication][org.springframework.boot.autoconfigure.SpringBootApplication]
 * or [@Configuration][org.springframework.context.annotation.Configuration] class to enable
 * page filtering. Registers [PageFilteringConfiguration] which provides:
 * - [com.sparjapati.pageFiltering.PageRequestResolver] — resolves [@PageRequestFilters][com.sparjapati.pageFiltering.PageRequestFilters]-annotated controller parameters
 * - [com.sparjapati.pageFiltering.PageRequestMetaRegistry] — indexes filter/sort/search metadata per resource
 * - [com.sparjapati.pageFiltering.PageRequestMetaController] — exposes metadata at `GET /page-meta/{resource}`
 *
 * Usage:
 * ```kotlin
 * @SpringBootApplication
 * @EnablePageFiltering
 * class MyApplication
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(PageFilteringConfiguration::class)
annotation class EnablePageFiltering
