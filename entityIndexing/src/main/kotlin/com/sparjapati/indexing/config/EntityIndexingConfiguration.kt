package com.sparjapati.indexing.config

import com.sparjapati.indexing.aspect.ReindexContextAspect
import com.sparjapati.indexing.aspect.ReindexParamAspect
import com.sparjapati.indexing.core.IndexConverter
import com.sparjapati.indexing.core.IndexConverterRegistry
import com.sparjapati.indexing.listener.ReindexingListener
import com.sparjapati.indexing.service.ReindexService
import com.sparjapati.indexing.sink.IndexSink
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
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
    fun reindexContextAspect(publisher: ApplicationEventPublisher): ReindexContextAspect =
        ReindexContextAspect(publisher)

    @Bean
    fun reindexParamAspect(): ReindexParamAspect = ReindexParamAspect()

    @Bean
    fun reindexService(
        entityManager: EntityManager,
        registry: IndexConverterRegistry,
        properties: EntityIndexingProperties,
    ): ReindexService = ReindexService(entityManager, registry, properties.chunkSize)

    @Bean
    fun reindexingListener(
        reindexService: ReindexService,
        @Autowired(required = false) sinks: List<IndexSink> = emptyList(),
    ): ReindexingListener = ReindexingListener(reindexService, sinks)
}
