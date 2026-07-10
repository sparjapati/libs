package cacheStore

/**
 * The pluggable storage extension point. Implement this against any backend (MySQL, MongoDB,
 * an in-memory map, DynamoDB, ...) to back a Spring [org.springframework.cache.CacheManager]
 * via [cacheStore.spring.StoreBackedCacheManager] — no other library code needs to change.
 */
interface CacheStore {
    fun put(key: String, value: String, expiresAt: Long? = null)
    operator fun get(key: String): CacheStoreEntry?
    fun evict(key: String)
    fun evictByPrefix(prefix: String)
}
