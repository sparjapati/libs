package com.sparjapati.indexing.sink

import com.sparjapati.indexing.core.AbstractEntityIndex
import kotlin.reflect.KClass

/**
 * Receives converted index documents and pushes them to a search store.
 * Register a bean implementing this interface to wire up a sink (e.g. Elasticsearch).
 * Multiple sinks are supported; all receive each batch.
 */
interface IndexSink {
    fun push(entityClass: KClass<*>, documents: List<AbstractEntityIndex>)
}
