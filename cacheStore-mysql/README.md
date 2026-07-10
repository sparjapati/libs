# cacheStore-mysql

JPA-backed `CacheStore` adapter for [`cacheStore`](../cacheStore/README.md) — stores cache
entries in a relational database via Spring Data JPA.

Implements [`CacheStore`](../cacheStore/src/main/kotlin/cacheStore/CacheStore.kt).

---

## Installation

```kotlin
// build.gradle.kts
implementation("com.sparjapati:cacheStore:0.0.1")
runtimeOnly("com.sparjapati:cacheStore-mysql:0.0.1")
```

`cacheStore-mysql` never needs to be referenced by class name — the app only needs it on the
classpath so its Spring Boot autoconfiguration registers the `mysqlCacheManager` bean. Use it
like any other named `CacheManager`:

```kotlin
@Cacheable(cacheNames = ["users"], cacheManager = "mysqlCacheManager", key = "#userId")
fun findById(userId: String): UserDto? = ...
```

---

## What gets auto-configured

| Bean | Type | Purpose |
|---|---|---|
| `mysqlCacheStore` | `CacheStore` | JPA-backed storage for cache entries |
| `mysqlCacheManager` | `CacheManager` | `StoreBackedCacheManager` wrapping `mysqlCacheStore` |

Both are name-scoped `@ConditionalOnMissingBean` — declare your own bean with the same name to
override either one. The repository and entity are scanned automatically; no `@EntityScan` or
`@EnableJpaRepositories` is needed in the host application for these classes.

### Configuration

```yaml
cache-store:
  mysql:
    default-ttl: 30m # java.time.Duration syntax, e.g. 5m, 1h, 30s
```

---

## Database schema

Table: **`cache_store_entry`**

| Column | Type | Notes |
|---|---|---|
| `cacheKey` | `VARCHAR(255)` | Primary key. Built as `"$cacheName::$key"` — keep `@Cacheable` keys short, or hash them yourself, to stay under 255 chars |
| `value` | `TEXT` NOT NULL | JSON-encoded cache value envelope |
| `expiresAt` | `BIGINT` | Epoch millis; `null` means never expires |

A migration is not shipped by this library — apply the DDL in your own app's migrations.
