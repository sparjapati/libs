package com.dbStore.aspect

import com.dbStore.annotation.DbStoreCachePut
import com.dbStore.service.DbStoreCacheSupport
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotationUtils
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

class DbStoreCachePutAspect(
    private val cacheSupport: DbStoreCacheSupport,
) : MethodInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val annotationCache = ConcurrentHashMap<Method, Optional<DbStoreCachePut>>()

    @Suppress("UNCHECKED_CAST")
    override fun invoke(invocation: MethodInvocation): Any? {
        val method = invocation.method
        val cachePut = annotationCache.getOrPut(method) {
            Optional.ofNullable(AnnotationUtils.findAnnotation(method, DbStoreCachePut::class.java))
        }.orElse(null) ?: return invocation.proceed()

        val args = invocation.arguments as Array<Any?>
        val key = cacheSupport.buildCacheKey(
            method = method,
            args = args,
            cacheName = cachePut.cacheName,
            keyExpression = cachePut.key,
        )

        val result = invocation.proceed()
        if (result != null) {
            cacheSupport.saveToCache(key = key, value = result, ttlSeconds = cachePut.ttlSeconds)
        } else {
            log.debug("@DbStoreCachePut method returned null, skipping cache write key={}", key)
        }
        return result
    }
}
