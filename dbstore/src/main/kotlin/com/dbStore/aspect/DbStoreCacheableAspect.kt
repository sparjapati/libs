package com.dbStore.aspect

import com.dbStore.annotation.DbStoreCacheable
import com.dbStore.service.DbStoreCacheSupport
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory

@Aspect
class DbStoreCacheableAspect(
    private val cacheSupport: DbStoreCacheSupport,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(cacheable)")
    fun around(pjp: ProceedingJoinPoint, cacheable: DbStoreCacheable): Any? {

        val key = cacheSupport.buildCacheKey(
            pjp,
            cacheable.cacheName,
            cacheable.key
        )

        cacheSupport.getFromCache(key)?.let {
            return cacheSupport.deserialize(pjp, it)
        }

        synchronized(key.intern()) {
            log.debug("@DbStoreCacheable acquiring lock key={}", key)
            cacheSupport.getFromCache(key)?.let {
                log.debug("@DbStoreCacheable cache hit after lock key={}", key)
                return cacheSupport.deserialize(pjp, it)
            }

            val result = pjp.proceed()
            if (result != null) {
                cacheSupport.saveToCache(
                    key,
                    result,
                    cacheable.ttlSeconds
                )
            } else {
                log.debug("@DbStoreCacheable method returned null, skipping cache write key={}", key)
            }
            return result
        }
    }
}
