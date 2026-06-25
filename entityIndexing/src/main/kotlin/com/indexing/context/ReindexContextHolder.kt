package com.indexing.context

import kotlin.reflect.KClass

object ReindexContextHolder {

    private val stack = ThreadLocal<ArrayDeque<MutableMap<KClass<*>, MutableSet<String>>>>()

    fun start() {
        val deque = stack.get() ?: ArrayDeque<MutableMap<KClass<*>, MutableSet<String>>>().also { stack.set(it) }
        deque.addLast(mutableMapOf())
    }

    fun isActive(): Boolean = stack.get()?.isNotEmpty() == true

    fun register(entityClass: KClass<*>, id: String) = register(entityClass, listOf(id))

    fun register(entityClass: KClass<*>, vararg ids: String) = register(entityClass, ids.toList())

    fun register(entityClass: KClass<*>, ids: Collection<String>) {
        stack.get()?.lastOrNull()?.computeIfAbsent(entityClass) { mutableSetOf() }?.addAll(ids)
    }

    inline fun <reified D : Any> register(id: String) = register(D::class, id)

    inline fun <reified D : Any> register(vararg ids: String) = register(D::class, ids.toList())

    inline fun <reified D : Any> register(ids: Collection<String>) = register(D::class, ids)

    fun finish(): Map<KClass<*>, Set<String>> =
        stack.get()?.removeLastOrNull()?.mapValues { it.value.toSet() }.orEmpty()

    fun clear() {
        if (stack.get()?.isEmpty() == true) stack.remove()
    }
}
