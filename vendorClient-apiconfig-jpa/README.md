# vendorClient-apiconfig-jpa

JPA-backed adapter for `vendorClient` — stores vendor API configuration (rate-limit params,
resilience params, enabled/disabled state) in a relational database via Spring Data JPA.

Implements [`VendorApiConfigProvider`](../vendorClient/src/main/kotlin/vendorClient/config/VendorApiConfigProvider.kt)
and [`VendorApiConfigManager`](../vendorClient/src/main/kotlin/vendorClient/config/VendorApiConfigManager.kt).

---

## Installation

```kotlin
// build.gradle.kts
implementation("com.sparjapati:vendorClient:0.0.1")
implementation("com.sparjapati:vendorClient-apiconfig-jpa:0.0.1")
```

Spring Boot autoconfiguration registers `JpaVendorApiConfigProvider` and `JpaVendorApiConfigManager`
automatically — no `@Enable*` annotation or explicit bean declaration required.

---

## What gets auto-configured

| Bean | Interface | Purpose |
|---|---|---|
| `JpaVendorApiConfigProvider` | `VendorApiConfigProvider` | Reads `VendorApiConfig` from DB per API key |
| `JpaVendorApiConfigManager` | `VendorApiConfigManager` | Creates, updates, and temp-disables API configs |

Both beans are `@ConditionalOnMissingBean` — declare your own bean to override either.

The repository and entity are scanned automatically. No `@EntityScan` or `@EnableJpaRepositories`
extension is needed in the host application for these classes.

---

## Database schema

Table: **`vendorApiConfig`**

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` AUTO_INCREMENT | Primary key |
| `apiName` | `VARCHAR(100)` NOT NULL | Unique — matches `VendorApiKey.name` |
| `maxRequests` | `INT` NOT NULL | Rate-limit window capacity |
| `windowSeconds` | `INT` NOT NULL | Sliding-window duration |
| `enabled` | `BOOLEAN` NOT NULL | Permanently enables/disables this API |
| `tempDisabledUntil` | `TIMESTAMP` | Non-null while in cooldown |
| `cbEnabled` | `BOOLEAN` NOT NULL | Opt-in circuit breaker |
| `cbFailureRateThreshold` | `INT` | % failures to open the CB |
| `cbWaitDurationSeconds` | `INT` | Seconds CB stays OPEN before half-open probe |
| `cbSlidingWindowSize` | `INT` | Rolling call count for the CB |
| `retryEnabled` | `BOOLEAN` NOT NULL | Opt-in retry |
| `retryMaxAttempts` | `INT` | Total attempts (original + retries) |
| `retryInitialIntervalMs` | `BIGINT` | First backoff delay (ms) |
| `retryMultiplier` | `DOUBLE` | Exponential multiplier |
| `retryMaxIntervalMs` | `BIGINT` | Backoff cap (ms) |

Unique constraint: `uq_vendor_api_config_api_name` on `apiName`.

Schema is managed by your application's DDL strategy (`spring.jpa.hibernate.ddl-auto`).

---

## Caching

`JpaVendorApiConfigProvider` is intentionally cache-free — it reads from the DB on every call.
Wrap it with a caching decorator (e.g. Spring Cache, `dbstore`) if you need in-process caching.

---

## Manual bean setup

If you prefer explicit configuration over autoconfiguration:

```kotlin
@Bean
fun vendorApiConfigProvider(repository: VendorApiConfigRepository): VendorApiConfigProvider =
    JpaVendorApiConfigProvider(repository)

@Bean
fun vendorApiConfigManager(repository: VendorApiConfigRepository): VendorApiConfigManager =
    JpaVendorApiConfigManager(repository)
```

---

## See also

- [`vendorClient`](../vendorClient/README.md) — core module and builder API
- [`vendorClient-apilog-jpa`](../vendorClient-apilog-jpa/README.md) — JPA-backed log persistence
- [`vendorClient-ratelimiter-redis`](../vendorClient-ratelimiter-redis/README.md) — Redis rate limiter
