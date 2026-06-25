package com.indexing.listener

import com.indexing.core.AbstractEntityIndex
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class EntityIndexListenerRegistry(listeners: List<EntityIndexListener<*>>) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val registry: Map<KClass<*>, List<EntityIndexListener<*>>> =
        buildMap<KClass<*>, MutableList<EntityIndexListener<*>>> {
            listeners.forEach { listener ->
                getOrPut(listener.entityClass) { mutableListOf() }.add(listener)
                log.info(
                    "Registered EntityIndexListener entityClass={} listener={}",
                    listener.entityClass.simpleName,
                    listener.javaClass.simpleName,
                )
            }

            log.info("EntityIndexListenerRegistry initialized successfully with {} listener(s)", values.sumOf { it.size })
        }

    @Suppress("UNCHECKED_CAST")
    fun getFor(entityClass: KClass<*>): List<EntityIndexListener<AbstractEntityIndex>> =
        (registry[entityClass] ?: emptyList()) as List<EntityIndexListener<AbstractEntityIndex>>
}
