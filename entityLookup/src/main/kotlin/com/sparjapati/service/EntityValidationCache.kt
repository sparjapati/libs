package com.sparjapati.service

import org.springframework.context.annotation.Scope
import org.springframework.context.annotation.ScopedProxyMode
import org.springframework.stereotype.Component
import org.springframework.web.context.WebApplicationContext

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
open class EntityValidationCache {
    private val existsCache = mutableMapOf<String, Boolean>()

    fun contains(key: String): Boolean = existsCache.containsKey(key)
    operator fun get(key: String): Boolean = existsCache[key] == true

    fun put(key: String, value: Boolean) {
        existsCache[key] = value
    }
}