package com.dbStore.aspect

import com.dbStore.annotation.DbStoreCacheEvict
import com.dbStore.service.DbStoreCacheSupport
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class DbStoreCacheEvictAspect(
    private val cacheSupport: DbStoreCacheSupport
) {

    @Around("@annotation(cacheEvict)")
    fun around(pjp: ProceedingJoinPoint, cacheEvict: DbStoreCacheEvict): Any? {
        val result = pjp.proceed()

        if (cacheEvict.allEntries) {
            require(cacheEvict.cacheName.isNotBlank()) {
                "@DbStoreCacheEvict: cacheName must be set when allEntries = true"
            }
            cacheSupport.deleteAllByPrefix(cacheEvict.cacheName)
        } else {
            val key = cacheSupport.buildCacheKey(pjp, cacheEvict.cacheName, cacheEvict.key)
            cacheSupport.delete(key)
        }

        return result
    }
}
