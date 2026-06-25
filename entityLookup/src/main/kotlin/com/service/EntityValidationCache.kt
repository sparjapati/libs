package com.service

open class EntityValidationCache {
    private val existsCache = mutableMapOf<String, Boolean>()

    open fun contains(key: String): Boolean = existsCache.containsKey(key)
    open operator fun get(key: String): Boolean = existsCache[key] == true

    open fun put(key: String, value: Boolean) {
        existsCache[key] = value
    }
}