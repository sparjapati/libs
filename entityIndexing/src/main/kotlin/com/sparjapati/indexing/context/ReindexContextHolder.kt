package com.sparjapati.indexing.context

import kotlin.reflect.KClass

object ReindexContextHolder {

    private val context = ThreadLocal<MutableMap<KClass<*>, MutableSet<String>>>()

    fun start() {
        context.set(mutableMapOf())
    }

    fun isActive(): Boolean = context.get() != null

    fun register(entityClass: KClass<*>, ids: Collection<String>) {
        val map = context.get() ?: return
        map.computeIfAbsent(entityClass) { mutableSetOf() }.addAll(ids)
    }

    fun finish(): Map<KClass<*>, Set<String>> =
        context.get()?.mapValues { it.value.toSet() }.orEmpty()

    fun clear() {
        context.remove()
    }
}
