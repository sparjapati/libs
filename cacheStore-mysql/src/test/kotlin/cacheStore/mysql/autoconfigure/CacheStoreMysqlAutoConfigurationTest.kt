package cacheStore.mysql.autoconfigure

import cacheStore.mysql.repository.CacheStoreEntryRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CacheStoreMysqlAutoConfigurationTest {

    private val repository: CacheStoreEntryRepository = mockk()
    private val config = CacheStoreMysqlAutoConfiguration()

    @Test fun `mysqlCacheStore bean is created`() {
        assertNotNull(config.mysqlCacheStore(repository))
    }

    @Test fun `mysqlCacheManager bean is created`() {
        val cacheStore = config.mysqlCacheStore(repository)
        assertNotNull(config.mysqlCacheManager(cacheStore, ObjectMapper(), CacheStoreMysqlProperties()))
    }
}
