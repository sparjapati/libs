package cacheStore.mongo.mapping

import cacheStore.CacheStoreEntry
import cacheStore.mongo.document.CacheStoreEntryDocument

fun CacheStoreEntryDocument.toDto(): CacheStoreEntry = CacheStoreEntry(
    cacheKey = cacheKey,
    value = value,
    expiresAt = expiresAt,
)
