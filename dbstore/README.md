# DbStore

A Spring Boot library that provides annotation-driven, MySQL-backed caching. Values survive application restarts, can be shared across instances, and are queryable — unlike in-memory caches. The API mirrors Spring Cache's `@Cacheable` / `@CachePut` / `@CacheEvict` pattern.

---

## Table of Contents

- [How It Works](#how-it-works)
- [Setup](#setup)
- [Annotations](#annotations)
  - [@DbStoreCacheable](#dbstorecacheable)
  - [@DbStoreCachePut](#dbstorecacheput)
  - [@DbStoreCacheEvict](#dbstorecacheevict)
- [Cache Keys](#cache-keys)
- [TTL and Expiry](#ttl-and-expiry)
- [Using DbStoreService Directly](#using-dbstoreservice-directly)
- [Internals](#internals)

---

## How It Works

```
Method call
    │
    ├── @DbStoreCacheable ─── check DB cache ──► hit: return deserialized value
    │                                        └── miss: run method → serialize → save → return
    │
    ├── @DbStoreCachePut  ─── run method always → serialize → save → return
    │
    └── @DbStoreCacheEvict ── run method → delete cache entry → return
```

All values are JSON-serialized via Jackson before storage and deserialized back to the method's exact return type (including generics) on retrieval. The underlying table is `dbStoreCache` with columns `cacheKey` (PK), `value` (TEXT), and `expiresAt`.

---

## Setup

Add the dependency:

```kotlin
// build.gradle.kts
implementation("com.sparjapati:dbStore:<version>")
```

Enable the library on your main application class or any `@Configuration` class:

```kotlin
@SpringBootApplication
@EnableDbStoreCaching
class MyApplication
```

Create the table (or let Hibernate create it via `spring.jpa.hibernate.ddl-auto`):

```sql
CREATE TABLE dbStoreCache (
    cacheKey  VARCHAR(255) NOT NULL PRIMARY KEY,
    value     TEXT         NOT NULL,
    expiresAt DATETIME
);
```

The library requires Spring Data JPA — both the `JpaRepository` and `PlatformTransactionManager` are auto-configured by `spring-boot-starter-data-jpa`.

---

## Annotations

### @DbStoreCacheable

Cache-aside: check the DB first; run the method only on a cache miss, then save the result.

```kotlin
@DbStoreCacheable(cacheName = "userProfile", key = "#userId", ttlSeconds = 300)
fun getUserProfile(userId: String): UserProfile {
    return profileRepository.findById(userId).orElseThrow()
}
```

- If a non-expired cache entry exists for the key, the method body is **not executed** — the cached value is deserialized and returned.
- If there is no entry (or it is expired), the method runs and the result is saved to the cache.
- `null` results are not cached — the method always runs for null-returning invocations.
- Thread-safe: double-checked locking on `key.intern()` prevents multiple threads from executing the method simultaneously on the same cache miss.

| Parameter | Required | Description |
|---|---|---|
| `cacheName` | Yes (if `key` is blank) | Prefix for the cache key |
| `key` | Yes (if `cacheName` is blank) | SpEL expression evaluated against method parameters |
| `ttlSeconds` | No (default: `-1`) | Seconds until expiry; `-1` means no expiry |

### @DbStoreCachePut

Write-through: always execute the method, then update the cache with the result.

```kotlin
@DbStoreCachePut(cacheName = "userProfile", key = "#profile.userId", ttlSeconds = 300)
fun updateUserProfile(profile: UserProfile): UserProfile {
    return profileRepository.save(profile)
}
```

- The method always runs regardless of whether a cache entry exists.
- `null` results are not cached.

| Parameter | Required | Description |
|---|---|---|
| `cacheName` | Yes (if `key` is blank) | Prefix for the cache key |
| `key` | Yes (if `cacheName` is blank) | SpEL expression |
| `ttlSeconds` | No (default: `-1`) | Seconds until expiry |

### @DbStoreCacheEvict

Invalidation: execute the method, then delete the cache entry.

```kotlin
@DbStoreCacheEvict(cacheName = "userProfile", key = "#userId")
fun deleteUserProfile(userId: String) {
    profileRepository.deleteById(userId)
}
```

- The method always runs first; the cache entry is deleted afterward.

| Parameter | Required | Description |
|---|---|---|
| `cacheName` | Yes (if `key` is blank) | Prefix for the cache key |
| `key` | Yes (if `cacheName` is blank) | SpEL expression |

---

## Cache Keys

The cache key is composed from `cacheName` and the evaluated `key` expression:

```
{cacheName}::{evaluatedKey}
```

Examples:

| `cacheName` | `key` | Method args | Resulting cache key |
|---|---|---|---|
| `"userProfile"` | `"#userId"` | `userId = "u-42"` | `userProfile::u-42` |
| `"settings"` | `"#tenantId + '_' + #type"` | `tenantId = "t1"`, `type = "email"` | `settings::t1_email` |
| `""` | `"#orderId"` | `orderId = "ORD-1"` | `ORD-1` |
| `"cache"` | `""` | *(any)* | `cache` |

Key expressions use Spring Expression Language (SpEL). Method parameter names are available as `#paramName`. At least one of `cacheName` or `key` must be non-blank.

---

## TTL and Expiry

- `ttlSeconds = -1` (default): entry never expires.
- `ttlSeconds = 300`: entry expires 300 seconds after it is saved.
- Expiry is checked on **read** (`@DbStoreCacheable`): if the entry's `expiresAt` is in the past, it is deleted and treated as a cache miss.
- Saving an entry with a past `expiresAt` throws `IllegalStateException` (enforced by a `@PrePersist` / `@PreUpdate` JPA callback).

---

## Using DbStoreService Directly

Inject `DbStoreService` to interact with the cache programmatically:

```kotlin
@Service
class MyService(private val dbStoreService: DbStoreService) {

    fun warmUpCache(key: String, value: Any) {
        dbStoreService.save(
            cacheKey  = key,
            value     = value,
            expiresAt = LocalDateTime.now().plusHours(1),
        )
    }

    fun readRaw(key: String): DbStoreCache? = dbStoreService[key]

    fun invalidate(key: String) = dbStoreService.delete(key)
}
```

`DbStoreCache` fields:

| Field | Type | Description |
|---|---|---|
| `cacheKey` | `String` | The full composite key |
| `value` | `String` | JSON-serialized value |
| `expiresAt` | `LocalDateTime?` | Expiry timestamp, or `null` for no expiry |

---

## Internals

### Beans registered by `@EnableDbStoreCaching`

| Bean | Role |
|---|---|
| `DbStoreService` | MySQL-backed cache CRUD via `MysqlDbStoreCacheService` |
| `DbStoreCacheSupport` | SpEL key building, expiry checking, Jackson serialization |
| `DbStoreCacheableAspect` | `@Around` advice for `@DbStoreCacheable` |
| `DbStoreCachePutAspect` | `@Around` advice for `@DbStoreCachePut` |
| `DbStoreCacheEvictAspect` | `@Around` advice for `@DbStoreCacheEvict` |

No beans are registered unless `@EnableDbStoreCaching` is present.

### Serialization

Values are serialized to JSON using a shared `ObjectMapper` with all Jackson modules registered (`findAndRegisterModules()`). On retrieval, the value is deserialized back to the method's declared return type, including parameterized generics (e.g. `List<UserProfile>` is reconstructed correctly via `signature.method.genericReturnType`).

### Thread safety

`@DbStoreCacheable` uses double-checked locking (`synchronized(key.intern())`) to ensure only one thread executes the method on a cache miss for the same key. `@DbStoreCachePut` and `@DbStoreCacheEvict` always run the method and do not require this guard.

### Limitations

- The library uses a single shared `ObjectMapper` — configure it via `findAndRegisterModules()` at startup, not per-request.
- Cache entries with the same `cacheKey` are overwritten on `@DbStoreCachePut`, not versioned.
- Bulk eviction (e.g. evict all keys for a `cacheName`) is not supported via annotations — use `DbStoreService` directly with a query.
