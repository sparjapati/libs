package com.sparjapati.dbStore.aspect

import com.sparjapati.dbStore.annotation.DbStoreCachePut
import com.sparjapati.dbStore.service.DbStoreCacheSupport
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class DbStoreCachePutAspect(
    private val cacheSupport: DbStoreCacheSupport
) {

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
        }
        return result
    }
}
