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
| `JpaVendorApiConfigManager` | `VendorApiConfigManager` | Creates, updates, lists, and temp-disables API configs |
| `VendorApiConfigTempDisableCleanupScheduler` | — | Periodically clears `tempDisabledUntil` once its cooldown has passed |

All three beans are `@ConditionalOnMissingBean` — declare your own bean to override any of them.

`VendorApiConfig` now carries its own `apiName: String`. `createConfig`/`updateConfig` derive the
target row from `config.apiName`; `getConfig`/`tempDisable` still take a separate `api: VendorApiKey`
since they have no config to derive a name from.

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
| `tempDisabledUntil` | `BIGINT` | Epoch millis; non-null while in cooldown |
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

## Logging

`JpaVendorApiConfigManager` logs at SLF4J `INFO` on every `createConfig`/`updateConfig`/`tempDisable`
write, and at `DEBUG` on `listConfigs`. `JpaVendorApiConfigProvider` logs at `DEBUG` when no config
is found for an API.

---

## Temp-disable cleanup

`VendorApiConfigTempDisableCleanupScheduler` runs on a fixed rate and clears `tempDisabledUntil`
back to `null` on every row whose cooldown has already passed. This is a cleanup convenience, not
a correctness requirement — `VendorApiConfig.isTemporarilyDisabled` already treats a past
`tempDisabledUntil` as "not disabled" regardless of whether this job has run, so a delayed cleanup
never causes incorrect rate-limit enforcement. It exists so stored rows (and anything reading them
directly, e.g. `listConfigs()` or an admin UI) reflect current state.

| Property | Default | Meaning |
|---|---|---|
| `vendor-client.api-config.temp-disable-cleanup.interval-ms` | `60000` | Fixed-rate interval between cleanup runs |
| `vendor-client.api-config.temp-disable-cleanup.enabled` | `true` | Set to `false` to disable the scheduler entirely |

Requires Spring scheduling infrastructure (`@EnableScheduling`, applied automatically by this
module's autoconfiguration) to be active in the application context.

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
