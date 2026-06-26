package com.pageFiltering.config

import com.pageFiltering.PageRequestMetaController
import com.pageFiltering.PageRequestMetaRegistry
import com.pageFiltering.PageRequestResolver
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * Auto-configuration for page filtering. Activated automatically by Spring Boot —
 * no `@Enable` annotation required on the importing application.
 *
 * Registers [PageRequestResolver], [PageRequestMetaRegistry], and [PageRequestMetaController].
 * The consuming application is responsible for adding [PageRequestResolver] to Spring MVC's
 * argument resolver chain (typically via [org.springframework.web.servlet.config.annotation.WebMvcConfigurer]).
 */
@AutoConfiguration
class PageFilteringConfiguration {

    @Bean
    fun pageRequestResolver(): PageRequestResolver = PageRequestResolver()

    @Bean
    fun pageRequestMetaRegistry(
        handlerMappings: List<RequestMappingHandlerMapping>,
    ): PageRequestMetaRegistry = PageRequestMetaRegistry(handlerMappings)

    @Bean
    fun pageRequestMetaController(
        registry: PageRequestMetaRegistry,
    ): PageRequestMetaController = PageRequestMetaController(registry)
}
