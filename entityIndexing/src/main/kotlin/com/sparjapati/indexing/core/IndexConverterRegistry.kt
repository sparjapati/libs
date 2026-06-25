package com.sparjapati.indexing.core

import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class IndexConverterRegistry(indexConverters: List<IndexConverter<*, *>>) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val indexConverterMap: Map<KClass<*>, IndexConverter<*, *>> =
        buildMap {
            indexConverters.forEach { converter ->
                val entityClass = converter.entityClass

                require(!containsKey(entityClass)) {
                    "Multiple IndexConverters registered for entityClass=${entityClass.simpleName}"
                }

                put(entityClass, converter)

                log.info(
                    "Registered IndexConverter entityClass={} converter={}",
                    entityClass.simpleName,
                    converter.javaClass.simpleName,
                )
            }

            log.info("IndexConverterRegistry initialized successfully with {} converter(s)", size)
        }

    @Suppress("UNCHECKED_CAST")
    fun <D : Any, I : AbstractEntityIndex> get(entityClass: KClass<*>): IndexConverter<D, I> =
        indexConverterMap[entityClass] as? IndexConverter<D, I>
            ?: error("No IndexConverter registered for entityClass=${entityClass.simpleName}")
}
