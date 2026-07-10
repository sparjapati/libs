# cacheStore

A pluggable Spring `CacheManager`/`Cache` implementation. Works with the standard
`@Cacheable`/`@CacheEvict`/`@CachePut` annotations out of the box — no custom annotations —
backed by any storage you provide.

---

## Installation

```kotlin
// build.gradle.kts
implementation("com.sparjapati:cacheStore:0.0.1")
```

Core has no autoconfiguration of its own — it has no default backend. Add an adapter
(`cacheStore-mysql`, `cacheStore-mongo`) or implement `CacheStore` yourself.

---

## The extension point: `CacheStore`

```kotlin
interface CacheStore {
    fun put(key: String, value: String, expiresAt: Long? = null)
    operator fun get(key: String): CacheStoreEntry?
    fun evict(key: String)
    fun evictByPrefix(prefix: String)
}
```

Implement this against any backend — a relational database, MongoDB, DynamoDB, an in-memory
map, whatever you have. `expiresAt` is an epoch-millis expiry (`null` = never expires); callers
are expected to check it and evict-on-read (see the MySQL/Mongo adapters for the reference
pattern).

Then wire up a `CacheManager` bean yourself:

```kotlin
@Configuration
class MyCacheConfig {
    @Bean("myCacheManager")
    fun myCacheManager(objectMapper: ObjectMapper): CacheManager =
        StoreBackedCacheManager(cacheStore = MyCacheStore(), objectMapper = objectMapper)
}
```

and select it per call site:

```kotlin
@Cacheable(cacheNames = ["widgets"], cacheManager = "myCacheManager", key = "#id")
fun findWidget(id: String): Widget? = ...
```

### A minimal example backend

```kotlin
class InMemoryCacheStore : CacheStore {
    private val entries = ConcurrentHashMap<String, CacheStoreEntry>()
    override fun put(key: String, value: String, expiresAt: Long?) {
        entries[key] = CacheStoreEntry(cacheKey = key, value = value, expiresAt = expiresAt)
    }
    override fun get(key: String): CacheStoreEntry? = entries[key]
    override fun evict(key: String) { entries.remove(key) }
    override fun evictByPrefix(prefix: String) { entries.keys.filter { it.startsWith(prefix) }.forEach(entries::remove) }
}
```

---

## `StoreBackedCacheManager`

```kotlin
StoreBackedCacheManager(
    cacheStore: CacheStore,
    objectMapper: ObjectMapper,
    defaultTtl: Duration? = Duration.ofMinutes(30), // null = never expires
    cacheTtlOverrides: Map<String, Duration?> = emptyMap(), // a name mapped to null overrides to "never expires"
    initialCacheNames: Set<String> = emptySet(),
)
```

Cache names are dynamic: the first `getCache("name")` call lazily creates and caches a
`StoreBackedCache` for that name, same as `CaffeineCacheManager`'s dynamic mode.

---

## Serialization

Values are wrapped in a small internal envelope that carries the runtime class name alongside
the Jackson-serialized JSON, so a plain `Cache.get(key)` (which gives no type context) can still
deserialize back to the original type. Cached `null` is represented distinctly from "no entry",
so `@Cacheable` methods that legitimately return `null` are cached correctly rather than
recomputed on every call.

**Known limitation**: generic type parameters are erased at runtime — a cached `List<Widget>`
deserializes as `List<LinkedHashMap<*, *>>`. This is the same limitation Spring Data Redis's
`GenericJackson2JsonRedisSerializer` already has; avoid caching raw generic collections directly,
or wrap them in a concrete DTO.

Caching a Kotlin data class as the value requires the `ObjectMapper` you pass in to have
`jackson-module-kotlin` registered (true for any typical Spring Boot Kotlin app) — without it,
Jackson can't find a usable constructor for a data class. The library's own internal envelope
doesn't require this module.

---

## Concurrency

`Cache.get(key, Callable)` (used by `@Cacheable(sync = true)`) is implemented with a single
coarse `synchronized(this)` lock per `StoreBackedCache` instance — not per-key — since `CacheStore`
exposes no atomic compare-and-swap primitive that would work uniformly across arbitrary backends.
`putIfAbsent` uses the same lock. This is sufficient to prevent duplicate concurrent
recomputation within one JVM; it does not provide cross-instance/distributed locking.
