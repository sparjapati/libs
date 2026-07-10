package cacheStore.mysql

import cacheStore.mysql.entity.CacheStoreEntryEntity
import cacheStore.mysql.repository.CacheStoreEntryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Optional

class JpaCacheStoreTest {

    private val repository: CacheStoreEntryRepository = mockk(relaxed = true)
    private val store = JpaCacheStore(repository)

    @Test fun `get returns dto when entity found`() {
        every { repository.findById("users::1") } returns Optional.of(
            CacheStoreEntryEntity(cacheKey = "users::1", value = "\"Ada\"", expiresAt = null)
        )
        val entry = store["users::1"]
        assertEquals("\"Ada\"", entry?.value)
    }

    @Test fun `get returns null when entity not found`() {
        every { repository.findById("missing") } returns Optional.empty()
        assertNull(store["missing"])
    }

    @Test fun `put saves the entity`() {
        var saved: CacheStoreEntryEntity? = null
        every { repository.save(any<CacheStoreEntryEntity>()) } answers { saved = firstArg(); firstArg() }

        store.put(key = "users::1", value = "\"Ada\"", expiresAt = 123L)

        assertEquals("users::1", saved?.cacheKey)
        assertEquals("\"Ada\"", saved?.value)
        assertEquals(123L, saved?.expiresAt)
    }

    @Test fun `evict deletes by cache key without requiring existence`() {
        store.evict("users::1")
        verify { repository.deleteByCacheKey("users::1") }
    }

    @Test fun `evictByPrefix delegates to the repository`() {
        store.evictByPrefix("users::")
        verify { repository.deleteAllByCacheKeyStartingWith("users::") }
    }
}
