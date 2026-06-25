package com.dbStore.aspect

import com.dbStore.annotation.DbStoreCacheEvict
import com.dbStore.service.DbStoreCacheSupport
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory

@Aspect
class DbStoreCacheEvictAspect(
    private val cacheSupport: DbStoreCacheSupport
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(cacheEvict)")
    fun around(pjp: ProceedingJoinPoint, cacheEvict: DbStoreCacheEvict): Any? {
        val result = pjp.proceed()

        if (cacheEvict.allEntries) {
            require(cacheEvict.cacheName.isNotBlank()) {
                "@DbStoreCacheEvict: cacheName must be set when allEntries = true"
            }
            log.info("@DbStoreCacheEvict evicting all entries for cacheName={}", cacheEvict.cacheName)
            cacheSupport.deleteAllByPrefix(cacheEvict.cacheName)
        } else {
            val key = cacheSupport.buildCacheKey(pjp, cacheEvict.cacheName, cacheEvict.key)
            log.debug("@DbStoreCacheEvict evicting key={}", key)
            cacheSupport.delete(key)
        }

        return result
    }
}
