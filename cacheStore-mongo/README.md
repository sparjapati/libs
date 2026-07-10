# cacheStore-mongo

MongoDB-backed `CacheStore` adapter for [`cacheStore`](../cacheStore/README.md) — stores cache
entries as documents via Spring Data MongoDB.

Implements [`CacheStore`](../cacheStore/src/main/kotlin/cacheStore/CacheStore.kt).

**Not currently wired into AaiseHi** — that app has no existing MongoDB infrastructure (no
driver, connection config, or docker-compose service). Add it there only once you actually need a
Mongo-backed cache; simply adding the runtime dependency without a running Mongo connection will
break app startup (`@EnableMongoRepositories` requires a live connection).

---

## Installation

```kotlin
// build.gradle.kts
implementation("com.sparjapati:cacheStore:0.0.1")
runtimeOnly("com.sparjapati:cacheStore-mongo:0.0.1")
```

You also need a working Mongo connection configured (e.g. `spring.data.mongodb.uri` or the
individual `spring.data.mongodb.host`/`port`/`database` properties, plus
`spring-boot-starter-data-mongodb` if you don't already depend on the Mongo driver).

`cacheStore-mongo` never needs to be referenced by class name — the app only needs it on the
classpath so its Spring Boot autoconfiguration registers the `mongoCacheManager` bean:

```kotlin
@Cacheable(cacheNames = ["users"], cacheManager = "mongoCacheManager", key = "#userId")
fun findById(userId: String): UserDto? = ...
```

---

## What gets auto-configured

| Bean | Type | Purpose |
|---|---|---|
| `mongoCacheStore` | `CacheStore` | Mongo-backed storage for cache entries |
| `mongoCacheManager` | `CacheManager` | `StoreBackedCacheManager` wrapping `mongoCacheStore` |

Both are name-scoped `@ConditionalOnMissingBean` — declare your own bean with the same name to
override either one. The repository is scanned automatically; no `@EnableMongoRepositories`
extension is needed in the host application for it.

### Configuration

```yaml
cache-store:
  mongo:
    default-ttl: 30m # java.time.Duration syntax, e.g. 5m, 1h, 30s
```

---

## Storage

Collection: **`cacheStoreEntries`**

| Field | Type | Notes |
|---|---|---|
| `cacheKey` | `String` | `@Id`. Built as `"$cacheName::$key"` |
| `value` | `String` | JSON-encoded cache value envelope |
| `expiresAt` | `Long?` | Epoch millis; `null` means never expires |

Expiry is checked and evicted-on-read by `StoreBackedCache`, the same as the MySQL adapter —
this collection does not rely on a native Mongo TTL index.
