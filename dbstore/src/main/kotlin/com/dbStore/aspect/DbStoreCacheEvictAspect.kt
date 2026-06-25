package com.dbStore.aspect

import com.dbStore.annotation.DbStoreCacheEvict
import com.dbStore.service.DbStoreCacheSupport
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotationUtils
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

class DbStoreCacheEvictAspect(
    private val cacheSupport: DbStoreCacheSupport,
) : MethodInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val annotationCache = ConcurrentHashMap<Method, Optional<DbStoreCacheEvict>>()

    @Suppress("UNCHECKED_CAST")
    override fun invoke(invocation: MethodInvocation): Any? {
        val method = invocation.method
        val cacheEvict = annotationCache.getOrPut(method) {
            Optional.ofNullable(AnnotationUtils.findAnnotation(method, DbStoreCacheEvict::class.java))
        }.orElse(null) ?: return invocation.proceed()

        val result = invocation.proceed()

        if (cacheEvict.allEntries) {
            require(cacheEvict.cacheName.isNotBlank()) {
                "@DbStoreCacheEvict: cacheName must be set when allEntries = true"
            }
            log.info("@DbStoreCacheEvict evicting all entries for cacheName={}", cacheEvict.cacheName)
            cacheSupport.deleteAllByPrefix(cacheEvict.cacheName)
        } else {
            val args = invocation.arguments as Array<Any?>
            val key = cacheSupport.buildCacheKey(
                method = method,
                args = args,
                cacheName = cacheEvict.cacheName,
                keyExpression = cacheEvict.key,
            )
            log.debug("@DbStoreCacheEvict evicting key={}", key)
            cacheSupport.delete(key)
        }

        return result
    }
}
