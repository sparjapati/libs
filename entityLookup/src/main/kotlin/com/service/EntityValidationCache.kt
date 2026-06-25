package com.service

import org.slf4j.LoggerFactory

open class EntityValidationCache {

    private val log = LoggerFactory.getLogger(javaClass)
    private val existsCache = mutableMapOf<String, Boolean>()

    open fun contains(key: String): Boolean = existsCache.containsKey(key)
    open operator fun get(key: String): Boolean = existsCache[key] == true

    open fun put(key: String, value: Boolean) {
        log.debug("EntityValidationCache put key={} exists={}", key, value)
        existsCache[key] = value
    }
}
