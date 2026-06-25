package com.indexing.config

import com.indexing.aspect.ReindexContextAspect
import com.indexing.aspect.ReindexParamAspect
import com.indexing.core.IndexConverter
import com.indexing.core.IndexConverterRegistry
import com.indexing.listener.EntityIndexListener
import com.indexing.listener.EntityIndexListenerRegistry
import com.indexing.service.ReindexService
import com.indexing.sink.IndexSink
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.core.env.Environment

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class EntityIndexingConfiguration(private val environment: Environment) {

    @Bean
    fun indexConverterRegistry(converters: List<IndexConverter<*, *>>): IndexConverterRegistry =
        IndexConverterRegistry(converters)

    @Bean
    fun reindexParamAspect(): ReindexParamAspect = ReindexParamAspect()

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
    fun reindexContextAspect(
        reindexService: ReindexService,
        listenerRegistry: EntityIndexListenerRegistry,
        @Autowired(required = false) sinks: List<IndexSink> = emptyList(),
    ): ReindexContextAspect = ReindexContextAspect(reindexService, sinks, listenerRegistry)
}
