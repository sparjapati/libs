package com.sparjapati.dbStore.aspect

import com.sparjapati.dbStore.annotation.DbStoreCacheEvict
import com.sparjapati.dbStore.service.DbStoreCacheSupport
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class DbStoreCacheEvictAspect(
    private val cacheSupport: DbStoreCacheSupport
) {

    @Around("@annotation(cacheEvict)")
    fun around(pjp: ProceedingJoinPoint, cacheEvict: DbStoreCacheEvict): Any? {

        val key = cacheSupport.buildCacheKey(
            pjp,
            cacheEvict.cacheName,
            cacheEvict.key
        )

        val result = pjp.proceed()
        cacheSupport.delete(key)
        return result
    }
}
