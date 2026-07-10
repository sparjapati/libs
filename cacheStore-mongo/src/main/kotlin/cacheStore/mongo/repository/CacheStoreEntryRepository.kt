package cacheStore.mongo.repository

import cacheStore.mongo.document.CacheStoreEntryDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface CacheStoreEntryRepository : MongoRepository<CacheStoreEntryDocument, String> {
    fun deleteByCacheKey(cacheKey: String)
    fun deleteAllByCacheKeyStartingWith(prefix: String)
}
