package cacheStore.mongo.autoconfigure

import cacheStore.mongo.repository.CacheStoreEntryRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CacheStoreMongoAutoConfigurationTest {

    private val repository: CacheStoreEntryRepository = mockk()
    private val config = CacheStoreMongoAutoConfiguration()

    @Test fun `mongoCacheStore bean is created`() {
        assertNotNull(config.mongoCacheStore(repository))
    }

    @Test fun `mongoCacheManager bean is created`() {
        val cacheStore = config.mongoCacheStore(repository)
        assertNotNull(config.mongoCacheManager(cacheStore, ObjectMapper(), CacheStoreMongoProperties()))
    }
}
