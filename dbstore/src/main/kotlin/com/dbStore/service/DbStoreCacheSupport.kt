package com.dbStore.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import java.lang.reflect.Method
import java.time.LocalDateTime

class DbStoreCacheSupport(
    private val dbStoreService: DbStoreService,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val parser = SpelExpressionParser()

    fun buildCacheKey(
        method: Method,
        args: Array<Any?>,
        cacheName: String,
        keyExpression: String,
    ): String {
        require(cacheName.isNotBlank() || keyExpression.isNotBlank()) {
            "Both cacheName and key can't be blank."
        }

        val context = StandardEvaluationContext()
        method.parameters.forEachIndexed { index, param ->
            context.setVariable(param.name, args[index])
        }

        val key = if (keyExpression.isNotBlank()) {
            parser.parseExpression(keyExpression)
                .getValue(context, String::class.java)
        } else null

        return buildString {
            append(cacheName)
            if (!key.isNullOrBlank()) append("::").append(key)
        }
    }

    fun getFromCache(key: String): String? {
        val entry = dbStoreService[key] ?: run {
            log.debug("Cache miss key={}", key)
            return null
        }

        if (entry.expiresAt?.isBefore(LocalDateTime.now()) == true) {
            log.debug("Cache entry expired, evicting key={}", key)
            dbStoreService.delete(key)
            return null
        }
        log.debug("Cache hit key={}", key)
        return entry.value
    }

    fun saveToCache(
        key: String,
        value: Any,
        ttlSeconds: Long,
    ) {
        val expiresAt = if (ttlSeconds > 0)
            LocalDateTime.now().plusSeconds(ttlSeconds)
        else null

        log.debug("Saving to cache key={} ttlSeconds={}", key, ttlSeconds)
        dbStoreService.save(
            cacheKey = key,
            value = value,
            expiresAt = expiresAt
        )
    }

    fun delete(key: String) {
        log.debug("Deleting cache entry key={}", key)
        dbStoreService.delete(key)
    }

    fun deleteAllByPrefix(prefix: String) {
        log.debug("Deleting all cache entries with prefix={}", prefix)
        dbStoreService.deleteAllByPrefix(prefix)
    }

    fun deserialize(method: Method, cachedValue: String): Any {
        val javaType = objectMapper.typeFactory.constructType(method.genericReturnType)
        return objectMapper.readValue(cachedValue, javaType)
    }
}
