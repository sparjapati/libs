package cacheStore.spring

import cacheStore.CacheStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * A [CacheManager] whose caches are all [StoreBackedCache] instances backed by one [CacheStore].
 * Cache names are dynamic — a [StoreBackedCache] is created and cached in-memory the first time
 * [getCache] is called for a given name, mirroring `CaffeineCacheManager`'s dynamic mode /
 * `RedisCacheManager`'s default behavior.
 */
class StoreBackedCacheManager(
    private val cacheStore: CacheStore,
    private val objectMapper: ObjectMapper,
    /** `null` means entries never expire by default. */
    private val defaultTtl: Duration? = Duration.ofMinutes(30),
    /**
     * Per-cache-name TTL overrides. A name mapped to `null` means "never expires" for that
     * cache specifically; a name absent from this map falls back to [defaultTtl].
     */
    private val cacheTtlOverrides: Map<String, Duration?> = emptyMap(),
    initialCacheNames: Set<String> = emptySet(),
) : CacheManager {

    private val caches = ConcurrentHashMap<String, Cache>()

    init {
        initialCacheNames.forEach(::getCache)
    }

    override fun getCache(name: String): Cache =
        caches.computeIfAbsent(name) {
            StoreBackedCache(
                name = it,
                cacheStore = cacheStore,
                objectMapper = objectMapper,
                ttl = if (cacheTtlOverrides.containsKey(it)) cacheTtlOverrides.getValue(it) else defaultTtl,
            )
        }

    override fun getCacheNames(): Collection<String> = caches.keys.toList()
}
