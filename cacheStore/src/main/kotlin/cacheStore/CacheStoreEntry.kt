package cacheStore

data class CacheStoreEntry(
    val cacheKey: String,
    val value: String,
    val expiresAt: Long? = null,
)
