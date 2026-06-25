package com.indexing.listener

import com.indexing.core.AbstractEntityIndex
import kotlin.reflect.KClass

/**
 * Per-entity reindex callback. Implement and register as a Spring bean to receive converted
 * index documents for a specific entity class after each reindex cycle.
 *
 * Multiple listeners for the same entity class are all called.
 */
interface EntityIndexListener<I : AbstractEntityIndex> {
    val entityClass: KClass<*>
    fun onReindex(documents: List<I>)
}
