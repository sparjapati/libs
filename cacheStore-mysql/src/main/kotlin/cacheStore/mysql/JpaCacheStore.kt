package cacheStore.mysql

import cacheStore.CacheStore
import cacheStore.CacheStoreEntry
import cacheStore.mysql.entity.CacheStoreEntryEntity
import cacheStore.mysql.mapping.toDto
import cacheStore.mysql.repository.CacheStoreEntryRepository
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

open class JpaCacheStore(
    private val repository: CacheStoreEntryRepository,
) : CacheStore {

    @Transactional
    override fun put(key: String, value: String, expiresAt: Long?) {
        repository.save(CacheStoreEntryEntity(cacheKey = key, value = value, expiresAt = expiresAt))
    }

    @Transactional(readOnly = true)
    override fun get(key: String): CacheStoreEntry? =
        repository.findById(key).map { it.toDto() }.getOrNull()

    @Transactional
    override fun evict(key: String) {
        repository.deleteByCacheKey(key)
    }

    @Transactional
    override fun evictByPrefix(prefix: String) {
        repository.deleteAllByCacheKeyStartingWith(prefix)
    }
}
