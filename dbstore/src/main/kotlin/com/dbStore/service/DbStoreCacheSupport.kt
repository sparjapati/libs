package com.dbStore.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import java.time.LocalDateTime

class DbStoreCacheSupport(
    private val dbStoreService: DbStoreService,
    private val objectMapper: ObjectMapper,
) {

    private val parser = SpelExpressionParser()

    fun buildCacheKey(
        pjp: ProceedingJoinPoint,
        cacheName: String,
        keyExpression: String,
    ): String {
        require(cacheName.isNotBlank() || keyExpression.isNotBlank()) {
            "Both cacheName and key can't be blank."
        }

        val context = StandardEvaluationContext()
        val signature = pjp.signature as MethodSignature
        signature.parameterNames.forEachIndexed { index, name ->
            context.setVariable(name, pjp.args[index])
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
        val entry = dbStoreService[key] ?: return null

        if (entry.expiresAt?.isBefore(LocalDateTime.now()) == true) {
            dbStoreService.delete(key)
            return null
        }
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

        dbStoreService.save(
            cacheKey = key,
            value = value,
            expiresAt = expiresAt
        )
    }

    fun delete(key: String) {
        dbStoreService.delete(key)
    }

    fun deleteAllByPrefix(prefix: String) {
        dbStoreService.deleteAllByPrefix(prefix)
    }

    fun deserialize(
        pjp: ProceedingJoinPoint,
        cachedValue: String,
    ): Any {
        val signature = pjp.signature as MethodSignature
        val javaType = objectMapper.typeFactory
            .constructType(signature.method.genericReturnType)
        return objectMapper.readValue(cachedValue, javaType)
    }
}
