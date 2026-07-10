package cacheStore.mysql.repository

import cacheStore.mysql.entity.CacheStoreEntryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CacheStoreEntryRepository : JpaRepository<CacheStoreEntryEntity, String> {
    // Derived delete-by methods delete 0 rows silently when nothing matches (unlike
    // CrudRepository.deleteById, which throws EmptyResultDataAccessException) — required here
    // since CacheStore.evict/evictByPrefix must be no-ops when the key was never cached.
    fun deleteByCacheKey(cacheKey: String)
    fun deleteAllByCacheKeyStartingWith(prefix: String)
}
