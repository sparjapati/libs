package cacheStore.spring

import cacheStore.CacheStore
import cacheStore.CacheStoreEntry
import cacheStore.serialization.CacheValueCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper
import java.time.Duration
import java.util.concurrent.Callable

/**
 * A [Cache] backed by any [CacheStore] implementation. Keys are namespaced as `"$name::$key"`
 * so multiple [StoreBackedCache] instances can safely share one [CacheStore]/table/collection.
 *
 * [get]/[put]/[evict] use a single coarse `synchronized(this)` lock (not per-key) to support
 * `@Cacheable(sync = true)`, since [CacheStore] exposes no atomic compare-and-swap primitive
 * that would work uniformly across arbitrary backends (MySQL, Mongo, user-defined, ...).
 */
class StoreBackedCache(
    private val name: String,
    private val cacheStore: CacheStore,
    objectMapper: ObjectMapper,
    /** `null` means entries never expire; otherwise must be a positive duration. */
    private val ttl: Duration?,
) : Cache {

    companion object {
        private val NOT_FOUND = Any()
    }

    init {
        require(ttl == null || (!ttl.isZero && !ttl.isNegative)) {
            "ttl must be null (never expires) or a positive duration, was $ttl"
        }
    }

    private val codec = CacheValueCodec(objectMapper)

    override fun getName(): String = name

    override fun getNativeCache(): CacheStore = cacheStore

    override fun get(key: Any): Cache.ValueWrapper? {
        val entry = getLiveEntry(buildKey(key)) ?: return null
        return SimpleValueWrapper(codec.decode(entry.value))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(key: Any, type: Class<T>?): T? {
        val entry = getLiveEntry(buildKey(key)) ?: return null
        val decoded = codec.decode(entry.value)
        if (decoded != null && type != null && !type.isInstance(decoded)) {
            throw IllegalStateException(
                "Cached value for key '$key' in cache '$name' is of type ${decoded::class.java.name}, expected ${type.name}"
            )
        }
        return decoded as T?
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T? {
        val cacheKey = buildKey(key)
        readOrNotFound(cacheKey).let { if (it !== NOT_FOUND) return it as T? }

        synchronized(this) {
            readOrNotFound(cacheKey).let { if (it !== NOT_FOUND) return it as T? }
            val value = try {
                valueLoader.call()
            } catch (ex: Exception) {
                throw Cache.ValueRetrievalException(key, valueLoader, ex)
            }
            put(key, value)
            return value
        }
    }

    override fun put(key: Any, value: Any?) {
        val expiresAt = ttl?.let { System.currentTimeMillis() + it.toMillis() }
        cacheStore.put(key = buildKey(key), value = codec.encode(value), expiresAt = expiresAt)
    }

    override fun putIfAbsent(key: Any, value: Any?): Cache.ValueWrapper? {
        synchronized(this) {
            val existing = get(key)
            if (existing == null) put(key, value)
            return existing
        }
    }

    override fun evict(key: Any) {
        cacheStore.evict(buildKey(key))
    }

    override fun evictIfPresent(key: Any): Boolean {
        val cacheKey = buildKey(key)
        val existed = getLiveEntry(cacheKey) != null
        cacheStore.evict(cacheKey)
        return existed
    }

    override fun clear() {
        cacheStore.evictByPrefix("$name::")
    }

    /** Returns [NOT_FOUND] (identity-compared) when there's no live entry, so a cached `null` can be told apart from a miss. */
    private fun readOrNotFound(cacheKey: String): Any? {
        val entry = getLiveEntry(cacheKey) ?: return NOT_FOUND
        return codec.decode(entry.value)
    }

    private fun getLiveEntry(cacheKey: String): CacheStoreEntry? {
        val entry = cacheStore[cacheKey] ?: return null
        if (entry.expiresAt != null && entry.expiresAt < System.currentTimeMillis()) {
            cacheStore.evict(cacheKey)
            return null
        }
        return entry
    }

    private fun buildKey(key: Any): String = "$name::$key"
}
