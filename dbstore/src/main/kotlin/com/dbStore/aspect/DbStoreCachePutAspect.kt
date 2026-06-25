package com.dbStore.aspect

import com.dbStore.annotation.DbStoreCachePut
import com.dbStore.service.DbStoreCacheSupport
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory

@Aspect
class DbStoreCachePutAspect(
    private val cacheSupport: DbStoreCacheSupport
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(cachePut)")
    fun around(pjp: ProceedingJoinPoint, cachePut: DbStoreCachePut): Any? {

        val key = cacheSupport.buildCacheKey(
            pjp,
            cachePut.cacheName,
            cachePut.key
        )

        val result = pjp.proceed()
        if (result != null) {
            cacheSupport.saveToCache(
                key,
                result,
                cachePut.ttlSeconds
            )
        } else {
            log.debug("@DbStoreCachePut method returned null, skipping cache write key={}", key)
        }
        return result
    }
}
