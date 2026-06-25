package com.sparjapati.pageFiltering.config

import com.sparjapati.pageFiltering.PageRequestMetaController
import com.sparjapati.pageFiltering.PageRequestMetaRegistry
import com.sparjapati.pageFiltering.PageRequestResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * Registers [PageRequestResolver], [PageRequestMetaRegistry], and [PageRequestMetaController]
 * as Spring beans. Imported via [@EnablePageFiltering][EnablePageFiltering].
 */
@Configuration
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

    @Bean
    fun pageFilteringWebMvcConfigurer(
        resolver: PageRequestResolver,
    ): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
            resolvers.add(resolver)
        }
    }
}
