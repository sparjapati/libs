package cacheStore.spring

import cacheStore.serialization.CacheValueCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Callable

class StoreBackedCacheTest {

    private val store = InMemoryCacheStore()
    private val cache = StoreBackedCache(
        name = "users",
        cacheStore = store,
        objectMapper = ObjectMapper(),
        ttl = Duration.ofMinutes(30),
    )

    @Test fun `put then get round-trips a value`() {
        cache.put("42", "Ada")
        assertEquals("Ada", cache.get("42")?.get())
    }

    @Test fun `get returns null for a miss`() {
        assertNull(cache.get("missing"))
    }

    @Test fun `caches and returns a null value distinctly from a miss`() {
        cache.put("42", null)
        assertTrue(store.entries.containsKey("users::42"))
        assertNull(cache.get("42")?.get())
    }

    @Test fun `evict removes only the targeted key`() {
        cache.put("1", "a")
        cache.put("2", "b")
        cache.evict("1")
        assertNull(cache.get("1"))
        assertEquals("b", cache.get("2")?.get())
    }

    @Test fun `evictIfPresent reports whether the key existed`() {
        cache.put("1", "a")
        assertTrue(cache.evictIfPresent("1"))
        assertFalse(cache.evictIfPresent("1"))
    }

    @Test fun `clear only removes entries for this cache's namespace`() {
        val other = StoreBackedCache("orders", store, ObjectMapper(), Duration.ofMinutes(30))
        cache.put("1", "a")
        other.put("1", "z")

        cache.clear()

        assertNull(cache.get("1"))
        assertEquals("z", other.get("1")?.get())
    }

    @Test fun `expired entries are evicted on read`() {
        val staleValue = CacheValueCodec(ObjectMapper()).encode("stale")
        store.put("users::1", staleValue, expiresAt = System.currentTimeMillis() - 1)
        assertNull(cache.get("1"))
        assertFalse(store.entries.containsKey("users::1"))
    }

    @Test fun `get with valueLoader only invokes it once on a miss`() {
        var invocations = 0
        val loader = Callable { invocations++; "computed" }

        assertEquals("computed", cache.get("1", loader))
        assertEquals("computed", cache.get("1", loader))

        assertEquals(1, invocations)
    }

    @Test fun `putIfAbsent does not overwrite an existing value`() {
        cache.put("1", "first")
        val existing = cache.putIfAbsent("1", "second")
        assertEquals("first", existing?.get())
        assertEquals("first", cache.get("1")?.get())
    }

    @Test fun `putIfAbsent stores the value when absent`() {
        val existing = cache.putIfAbsent("1", "first")
        assertNull(existing)
        assertEquals("first", cache.get("1")?.get())
    }
}
