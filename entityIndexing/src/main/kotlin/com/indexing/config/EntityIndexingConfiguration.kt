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
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableConfigurationProperties(EntityIndexingProperties::class)
class EntityIndexingConfiguration {

    @Bean
    fun indexConverterRegistry(converters: List<IndexConverter<*, *>>): IndexConverterRegistry =
        IndexConverterRegistry(converters)

    @Bean
    fun reindexParamAspect(): ReindexParamAspect = ReindexParamAspect()

    @Bean
    fun reindexService(
        entityManager: EntityManager,
        registry: IndexConverterRegistry,
        properties: EntityIndexingProperties,
    ): ReindexService = ReindexService(entityManager, registry, properties.chunkSize)

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
