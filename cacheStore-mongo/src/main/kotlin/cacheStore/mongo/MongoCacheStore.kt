package cacheStore.mongo

import cacheStore.CacheStore
import cacheStore.CacheStoreEntry
import cacheStore.mongo.document.CacheStoreEntryDocument
import cacheStore.mongo.mapping.toDto
import cacheStore.mongo.repository.CacheStoreEntryRepository
import kotlin.jvm.optionals.getOrNull

open class MongoCacheStore(
    private val repository: CacheStoreEntryRepository,
) : CacheStore {

    override fun put(key: String, value: String, expiresAt: Long?) {
        repository.save(CacheStoreEntryDocument(cacheKey = key, value = value, expiresAt = expiresAt))
    }

    override fun get(key: String): CacheStoreEntry? =
        repository.findById(key).map { it.toDto() }.getOrNull()

    override fun evict(key: String) {
        repository.deleteByCacheKey(key)
    }

    override fun evictByPrefix(prefix: String) {
        repository.deleteAllByCacheKeyStartingWith(prefix)
    }
}
