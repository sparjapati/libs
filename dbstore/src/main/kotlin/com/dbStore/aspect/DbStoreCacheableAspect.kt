package com.dbStore.aspect

import com.dbStore.annotation.DbStoreCacheable
import com.dbStore.service.DbStoreCacheSupport
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotationUtils
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

class DbStoreCacheableAspect(
    private val cacheSupport: DbStoreCacheSupport,
) : MethodInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val annotationCache = ConcurrentHashMap<Method, Optional<DbStoreCacheable>>()

    @Suppress("UNCHECKED_CAST")
    override fun invoke(invocation: MethodInvocation): Any? {
        val method = invocation.method
        val cacheable = annotationCache.getOrPut(method) {
            Optional.ofNullable(AnnotationUtils.findAnnotation(method, DbStoreCacheable::class.java))
        }.orElse(null) ?: return invocation.proceed()

        val args = invocation.arguments as Array<Any?>
        val key = cacheSupport.buildCacheKey(
            method = method,
            args = args,
            cacheName = cacheable.cacheName,
            keyExpression = cacheable.key,
        )

        cacheSupport.getFromCache(key)?.let {
            return cacheSupport.deserialize(method, it)
        }

        synchronized(key.intern()) {
            log.debug("@DbStoreCacheable acquiring lock key={}", key)
            cacheSupport.getFromCache(key)?.let {
                log.debug("@DbStoreCacheable cache hit after lock key={}", key)
                return cacheSupport.deserialize(method, it)
            }

            val result = invocation.proceed()
            if (result != null) {
                cacheSupport.saveToCache(key = key, value = result, ttlSeconds = cacheable.ttlSeconds)
            } else {
                log.debug("@DbStoreCacheable method returned null, skipping cache write key={}", key)
            }
            return result
        }
    }
}
