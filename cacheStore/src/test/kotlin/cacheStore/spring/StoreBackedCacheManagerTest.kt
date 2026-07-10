package cacheStore.spring

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class StoreBackedCacheManagerTest {

    private val manager = StoreBackedCacheManager(cacheStore = InMemoryCacheStore(), objectMapper = ObjectMapper())

    @Test fun `getCache lazily creates and reuses the same instance per name`() {
        val first = manager.getCache("users")
        val second = manager.getCache("users")
        assertSame(first, second)
    }

    @Test fun `getCacheNames reflects every cache created so far`() {
        manager.getCache("users")
        manager.getCache("orders")
        assertEquals(setOf("users", "orders"), manager.getCacheNames().toSet())
    }

    @Test fun `initialCacheNames are eagerly created`() {
        val eager = StoreBackedCacheManager(
            cacheStore = InMemoryCacheStore(),
            objectMapper = ObjectMapper(),
            initialCacheNames = setOf("users"),
        )
        assertTrue(eager.getCacheNames().contains("users"))
    }

    @Test fun `per-cache TTL override applies only to the matching cache`() {
        val store = InMemoryCacheStore()
        val manager = StoreBackedCacheManager(
            cacheStore = store,
            objectMapper = ObjectMapper(),
            defaultTtl = Duration.ofMinutes(30),
            cacheTtlOverrides = mapOf("shortLived" to Duration.ofSeconds(5)),
        )
        val before = System.currentTimeMillis()
        manager.getCache("shortLived").put("1", "a")
        manager.getCache("users").put("1", "a")

        val overriddenExpiry = store.entries.getValue("shortLived::1").expiresAt!!
        val defaultExpiry = store.entries.getValue("users::1").expiresAt!!
        assertTrue(overriddenExpiry - before in 4_000..6_000)
        assertTrue(defaultExpiry - before in 29 * 60_000..31 * 60_000)
    }

    @Test fun `a name explicitly overridden to null never expires, distinct from an unset override`() {
        val store = InMemoryCacheStore()
        val manager = StoreBackedCacheManager(
            cacheStore = store,
            objectMapper = ObjectMapper(),
            defaultTtl = Duration.ofMinutes(30),
            cacheTtlOverrides = mapOf("permanent" to null),
        )
        manager.getCache("permanent").put("1", "a")
        assertEquals(null, store.entries.getValue("permanent::1").expiresAt)
    }
}
