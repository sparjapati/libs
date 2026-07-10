package cacheStore.mongo.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "cacheStoreEntries")
data class CacheStoreEntryDocument(
    @Id val cacheKey: String,
    val value: String,
    val expiresAt: Long? = null,
)
