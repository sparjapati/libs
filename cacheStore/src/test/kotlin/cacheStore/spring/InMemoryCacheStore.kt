package cacheStore.spring

import cacheStore.CacheStore
import cacheStore.CacheStoreEntry
import java.util.concurrent.ConcurrentHashMap

/** In-memory [CacheStore] test fake, standing in for a real MySQL/Mongo/user-defined backend. */
internal class InMemoryCacheStore : CacheStore {
    val entries = ConcurrentHashMap<String, CacheStoreEntry>()

    override fun put(key: String, value: String, expiresAt: Long?) {
        entries[key] = CacheStoreEntry(cacheKey = key, value = value, expiresAt = expiresAt)
    }

    override fun get(key: String): CacheStoreEntry? = entries[key]

    override fun evict(key: String) {
        entries.remove(key)
    }

    override fun evictByPrefix(prefix: String) {
        entries.keys.filter { it.startsWith(prefix) }.forEach(entries::remove)
    }
}
