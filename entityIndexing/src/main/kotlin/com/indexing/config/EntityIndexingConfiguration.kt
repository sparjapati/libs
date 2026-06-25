package com.indexing.config

import com.indexing.annotation.EnableEntityIndexing
import com.indexing.aspect.ReindexContextAspect
import com.indexing.aspect.ReindexParamAspect
import com.indexing.core.IndexConverter
import com.indexing.core.IndexConverterRegistry
import com.indexing.listener.EntityIndexListener
import com.indexing.listener.EntityIndexListenerRegistry
import com.indexing.service.ReindexService
import com.indexing.sink.IndexSink
import jakarta.persistence.EntityManager
import org.springframework.aop.aspectj.AspectJExpressionPointcut
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.ImportAware
import org.springframework.core.Ordered
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotationMetadata

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class EntityIndexingConfiguration(private val environment: Environment) : ImportAware {

    private var basePackages: Array<String> = emptyArray()

    override fun setImportMetadata(importMetadata: AnnotationMetadata) {
        val attrs = importMetadata.getAnnotationAttributes(EnableEntityIndexing::class.java.name)
        basePackages = when (val raw = attrs?.get("basePackages")) {
            is Array<*> -> raw.filterIsInstance<String>().toTypedArray()
            else        -> emptyArray()
        }
    }

    @Bean
    fun indexConverterRegistry(converters: List<IndexConverter<*, *>>): IndexConverterRegistry =
        IndexConverterRegistry(converters)

    @Bean
    fun reindexService(
        entityManager: EntityManager,
        registry: IndexConverterRegistry,
    ): ReindexService = ReindexService(
        entityManager = entityManager,
        converterRegistry = registry,
        chunkSize = environment.getProperty("spring.jpa.properties.hibernate.jdbc.batch_size", Int::class.java, 50),
    )

    @Bean
    fun entityIndexListenerRegistry(
        @Autowired(required = false) listeners: List<EntityIndexListener<*>> = emptyList(),
    ): EntityIndexListenerRegistry = EntityIndexListenerRegistry(listeners)

    @Bean
    fun reindexParamAdvisor(): DefaultPointcutAdvisor =
        DefaultPointcutAdvisor(buildPointcut(), ReindexParamAspect())

    @Bean
    fun reindexContextAdvisor(
        reindexService: ReindexService,
        listenerRegistry: EntityIndexListenerRegistry,
        @Autowired(required = false) sinks: List<IndexSink> = emptyList(),
    ): DefaultPointcutAdvisor =
        DefaultPointcutAdvisor(buildPointcut(), ReindexContextAspect(reindexService, sinks, listenerRegistry))
            .apply { order = Ordered.LOWEST_PRECEDENCE - 1 }

    // Builds the pointcut that limits interception to the application's own classes.
    // When basePackages is set: within(com.myapp..*) || within(com.other..*)
    // When not set: falls back to Spring stereotype beans, which avoids Spring internals
    // and third-party library classes without requiring explicit package configuration.
    private fun buildPointcut(): AspectJExpressionPointcut {
        val expression = if (basePackages.isNotEmpty()) {
            basePackages.joinToString(" || ") { "within($it..*)" }
        } else {
            listOf(
                "@within(org.springframework.stereotype.Service)",
                "@within(org.springframework.stereotype.Component)",
                "@within(org.springframework.stereotype.Repository)",
                "@within(org.springframework.web.bind.annotation.RestController)",
            ).joinToString(" || ")
        } + " && !within(com.indexing..*)"

        return AspectJExpressionPointcut().apply { this.expression = expression }
    }
}
