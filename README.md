# libs

Shared Kotlin libraries for Spring Boot 4 / Kotlin 2 services.

All artifacts are published under `com.sparjapati` and consumed via `mavenLocal()`.

---

## Libraries

| Artifact | Version | Description |
|---|---|---|
| `dbstore` | 0.0.5 | Entity-level DB caching (`@EnableDbStoreCaching`) |
| `entityLookup` | 0.0.2 | Entity validation and lookup (`@EnableEntityValidation`) |
| `bulkFileProcessing` | 0.0.2 | Bulk file import pipeline (`@EnableBulkFileProcessing`) |
| `entityIndexing` | 0.0.2 | Reindex-on-write pipeline (`@EnableEntityIndexing`) |
| `pageFiltering` | 0.0.2 | Pagination, filtering, and sorting resolver |
| `vendorClient` | 0.0.1 | HTTP vendor client — rate limiting, resilience, trace, logging |
| `vendorClient-apiconfig-jpa` | 0.0.1 | JPA-backed vendor API config store |
| `vendorClient-apilog-jpa` | 0.0.1 | JPA-backed vendor API log persistence |
| `vendorClient-ratelimiter-redis` | 0.0.1 | Redis sliding-window rate limiter for vendorClient |

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

## Publishing

```bash
# Publish a single module
./gradlew :vendorClient:publishToMavenLocal

# Publish all vendorClient modules at once
./gradlew :vendorClient:publishToMavenLocal \
          :vendorClient-apiconfig-jpa:publishToMavenLocal \
          :vendorClient-apilog-jpa:publishToMavenLocal \
          :vendorClient-ratelimiter-redis:publishToMavenLocal

# Run all tests
./gradlew test
```
