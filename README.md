# libs

Shared Kotlin libraries for Spring Boot 4 / Kotlin 2 services.

All artifacts are published under `com.sparjapati` and consumed via `mavenLocal()`.

---

## Libraries

| Artifact | Version | Description |
|---|---|---|
| `dbstore` | 0.0.1 | Entity-level DB caching (`@EnableDbStoreCaching`) |
| `entityLookup` | 0.0.1 | Entity validation and lookup (`@EnableEntityValidation`) |
| `bulkFileProcessing` | 0.0.3 | Bulk file import pipeline (`@EnableBulkFileProcessing`) |
| `bulkFileProcessing-mysql` | 0.0.1 | JPA-backed `BulkJobStore` adapter for bulkFileProcessing |
| `entityIndexing` | 0.0.1 | Reindex-on-write pipeline (`@EnableEntityIndexing`) |
| `pageFiltering` | 0.0.1 | Pagination, filtering, and sorting resolver |
| `vendorClient` | 0.0.1 | HTTP vendor client — rate limiting, resilience, trace, logging |
| `vendorClient-apiconfig-jpa` | 0.0.1 | JPA-backed vendor API config store |
| `vendorClient-apilog-jpa` | 0.0.1 | JPA-backed vendor API log persistence |
| `vendorClient-ratelimiter-redis` | 0.0.1 | Redis sliding-window rate limiter for vendorClient |
| `cacheStore` | 0.0.1 | Pluggable Spring `CacheManager`/`Cache` — works with standard `@Cacheable`, backed by any storage |
| `cacheStore-mysql` | 0.0.1 | JPA-backed `CacheStore` adapter for cacheStore |
| `cacheStore-mongo` | 0.0.1 | MongoDB-backed `CacheStore` adapter for cacheStore |
| `statusTransitionHistory` | 0.0.1 | Append-only status-transition history log for domain entities |
| `statusTransitionHistory-mysql` | 0.0.1 | JPA-backed `StatusTransitionStore` adapter for statusTransitionHistory |

---

## vendorClient family

The `vendorClient` modules are independently consumable — add only what you need:

```kotlin
// build.gradle.kts
repositories { mavenLocal(); mavenCentral() }

dependencies {
    // Core — always required for vendorClient features
    implementation("com.sparjapati:vendorClient:0.0.1")

    // Optional adapters — include the ones you need
    implementation("com.sparjapati:vendorClient-apiconfig-jpa:0.0.1")    // JPA config store
    implementation("com.sparjapati:vendorClient-apilog-jpa:0.0.1")       // JPA log persistence
    implementation("com.sparjapati:vendorClient-ratelimiter-redis:0.0.1") // Redis rate limiter
}
```

All three adapter modules self-register via Spring Boot autoconfiguration — no `@Enable*` annotation
required. See [`vendorClient/README.md`](vendorClient/README.md) for full usage.

---

## cacheStore family

Same shape as the `vendorClient` family — one core module implementing Spring's `CacheManager`/
`Cache` SPI against a pluggable `CacheStore` interface, plus one adapter module per backend:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.sparjapati:cacheStore:0.0.1")

    // Pick the adapter(s) you need
    runtimeOnly("com.sparjapati:cacheStore-mysql:0.0.1")
    runtimeOnly("com.sparjapati:cacheStore-mongo:0.0.1") // requires an existing Mongo connection
}
```

Each adapter self-registers a uniquely-named `CacheStore` bean and `CacheManager` bean
(`mysqlCacheManager`, `mongoCacheManager`) — select one per call site with
`@Cacheable(cacheManager = "...")`, the same way you'd pick between Caffeine/Redis. Implement
`CacheStore` yourself for any other backend; see [`cacheStore/README.md`](cacheStore/README.md).

---

## Publishing

```bash
# Publish a single module
./gradlew :vendorClient:publishToMavenLocal

# Publish all vendorClient modules at once
./gradlew :vendorClient:publishToMavenLocal \
          :vendorClient-apiconfig-jpa:publishToMavenLocal \
          :vendorClient-apilog-jpa:publishToMavenLocal \
          :vendorClient-ratelimiter-redis:publishToMavenLocal

# Publish all cacheStore modules at once
./gradlew :cacheStore:publishToMavenLocal \
          :cacheStore-mysql:publishToMavenLocal \
          :cacheStore-mongo:publishToMavenLocal

# Run all tests
./gradlew test
```
