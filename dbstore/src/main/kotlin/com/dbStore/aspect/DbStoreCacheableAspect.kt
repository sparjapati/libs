package com.dbStore.aspect

import com.dbStore.annotation.DbStoreCacheable
import com.dbStore.service.DbStoreCacheSupport
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class DbStoreCacheableAspect(
    private val cacheSupport: DbStoreCacheSupport,
) {

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
            cacheSupport.getFromCache(key)?.let {
                return cacheSupport.deserialize(pjp, it)
            }

            val result = pjp.proceed()
            if (result != null) {
                cacheSupport.saveToCache(
                    key,
                    result,
                    cacheable.ttlSeconds
                )
            }
            return result
        }
    }
}

