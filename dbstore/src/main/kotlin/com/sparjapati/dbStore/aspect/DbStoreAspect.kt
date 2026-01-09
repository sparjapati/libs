package com.sparjapati.dbStore.aspect

import com.fasterxml.jackson.databind.ObjectMapper
import com.sparjapati.dbStore.annotation.DbStoreCacheable
import com.sparjapati.dbStore.service.DbStoreService
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import java.time.LocalDateTime

@Aspect
class DbStoreAspect(
    private val dbStoreService: DbStoreService,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DbStoreAspect::class.java)
    }

    @Around("@annotation(cacheable)")
    fun around(pjp: ProceedingJoinPoint, cacheable: DbStoreCacheable): Any? {

        val methodSignature = pjp.signature as MethodSignature
        val returnType = methodSignature.returnType
        if (returnType == null) {
            LOGGER.info("Skipping caching in dbStore as return type {}", methodSignature.method.toString())
            return pjp.proceed()
        }

        check(cacheable.cacheName.isNotBlank() || cacheable.key.isNotBlank()) {
            "Both cacheName and key can't be blank."
        }
        val fullKey = getKey(pjp, cacheable)

        // Pre-lock Cache Check
        var cachedData = getFromCache(fullKey)
        val javaType = objectMapper.typeFactory
            .constructType(methodSignature.method.genericReturnType)
        if (cachedData != null) {
            return objectMapper.readValue(cachedData, javaType)
        }
        synchronized(Object()) {
            // Double-check inside lock
            cachedData = getFromCache(fullKey)
            if (cachedData != null) {
                return objectMapper.readValue(cachedData, javaType)
            }
            // Load Data & Cache
            val result = pjp.proceed()

            val now = LocalDateTime.now()
            var expiresAt: LocalDateTime? = null
            if (cacheable.ttlSeconds > 0) {
                expiresAt = now.plusSeconds(cacheable.ttlSeconds)
            }
            dbStoreService.save(
                cacheKey = fullKey,
                value = result,
                expiresAt = expiresAt
            )
            LOGGER.info("Stored in DbStore Cache (Key: {})", fullKey)
            return result
        }
    }

    private fun getKey(
        pjp: ProceedingJoinPoint,
        cacheable: DbStoreCacheable
    ): String {
        val fullKey = StringBuilder(cacheable.cacheName.ifBlank { "" })

        val context = StandardEvaluationContext()
        val args = pjp.args
        val methodSignature = pjp.signature as MethodSignature
        val paramNames = methodSignature.parameterNames
        for (i in args.indices) {
            context.setVariable(paramNames[i], args[i])
        }
        val parser = SpelExpressionParser()
        if (cacheable.key.isNotBlank()) {
            val key = parser.parseExpression(cacheable.key)
                .getValue(context, String::class.java)
            if (key?.isNotBlank() == true) {
                fullKey.append("::${key}")
            }
        }
        return fullKey.toString()
    }

    private fun getFromCache(fullKey: String): String? {
        val entry = dbStoreService[fullKey] ?: return null

        var isExpired = false

        if (entry.expiresAt != null) {
            isExpired = entry.expiresAt.isBefore(LocalDateTime.now())
        }
        if (!isExpired) {
            return entry.value
        } else {
            dbStoreService.delete(fullKey)
        }
        return null
    }
}

