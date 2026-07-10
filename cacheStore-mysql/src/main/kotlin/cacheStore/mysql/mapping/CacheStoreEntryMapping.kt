package cacheStore.mysql.mapping

import cacheStore.CacheStoreEntry
import cacheStore.mysql.entity.CacheStoreEntryEntity

fun CacheStoreEntryEntity.toDto(): CacheStoreEntry = CacheStoreEntry(
    cacheKey = cacheKey,
    value = value,
    expiresAt = expiresAt,
)
