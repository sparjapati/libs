# vendorClient-apilog-jpa

JPA-backed adapter for `vendorClient` — persists structured vendor API call logs (request,
response, headers, duration) to a relational database via Spring Data JPA.

Implements [`VendorApiLogSink`](../vendorClient/src/main/kotlin/vendorClient/logging/VendorApiLogSink.kt)
and [`VendorApiLogQuery`](../vendorClient/src/main/kotlin/vendorClient/logging/VendorApiLogQuery.kt).

---

## Installation

```kotlin
// build.gradle.kts
implementation("com.sparjapati:vendorClient:0.0.1")
implementation("com.sparjapati:vendorClient-apilog-jpa:0.0.1")
```

Spring Boot autoconfiguration registers `JpaVendorApiLogSink` and `JpaVendorApiLogQuery`
automatically — no `@Enable*` annotation or explicit bean declaration required.

---

## What gets auto-configured

| Bean | Interface | Purpose |
|---|---|---|
| `JpaVendorApiLogSink` | `VendorApiLogSink` | Persists one `VendorApiLog` row per API call attempt |
| `JpaVendorApiLogQuery` | `VendorApiLogQuery` | Reads logs by request-id prefix or API name |

Both beans are `@ConditionalOnMissingBean` — declare your own to override either.

The repository and entity are scanned automatically. No `@EntityScan` or `@EnableJpaRepositories`
extension is needed in the host application for these classes.

---

## Database schema

Table: **`vendorApiLogs`**

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` AUTO_INCREMENT | Primary key |
| `apiName` | `VARCHAR(100)` NOT NULL | Matches `VendorApiKey.name` |
| `requestId` | `VARCHAR(200)` NOT NULL | Inbound request-id (not the per-attempt trace-id) |
| `attemptId` | `VARCHAR(36)` NOT NULL | Fresh UUID per retry attempt — distinguishes rows sharing one `requestId` |
| `httpMethod` | `VARCHAR(10)` NOT NULL | `GET`, `POST`, etc. |
| `url` | `VARCHAR(2048)` NOT NULL | Full outbound URL |
| `requestHeaders` | `TEXT` | JSON object — full fidelity, no masking |
| `requestBody` | `LONGTEXT` | Nullable — absent for bodyless methods |
| `responseCode` | `INT` | Nullable — absent if request never reached the server |
| `responseHeaders` | `TEXT` | JSON object — full fidelity, no masking |
| `responseBody` | `LONGTEXT` | Nullable — binary bodies stored as `BINARY_BODY_PLACEHOLDER` |
| `success` | `BOOLEAN` NOT NULL | `true` for 2xx responses |
| `errorMessage` | `VARCHAR(1000)` | Nullable — populated on non-2xx or exception |
| `durationMs` | `BIGINT` NOT NULL | Round-trip time for this attempt in milliseconds |
| `createdAt` | `BIGINT` NOT NULL | Epoch millis; set at log write time |

Indexes:
- `idx_vendor_api_logs_api_name_created_at` on `(apiName, createdAt)` — supports paginated queries by API
- `idx_vendor_api_logs_request_id` on `(requestId)` — supports prefix lookups by request-id

Schema is managed by your application's DDL strategy (`spring.jpa.hibernate.ddl-auto`).

---

## Query API

### By request-id prefix

```kotlin
// Returns all attempts for a given inbound request (including retries)
logQuery.findByRequestIdPrefix("req-abc123")
```

Retry attempts share the same base `requestId` but have distinct `attemptId` values.
Querying by prefix returns all attempts for a given inbound request in descending `createdAt` order.

### By API name (paginated)

```kotlin
val page = logQuery.findByApiName(apiName = "CHARGE", page = 0, pageSize = 20)
// page.content    → List<VendorApiLog>
// page.totalElements → Long
```

Results are ordered by `createdAt DESC`.

---

## Manual bean setup

If you prefer explicit configuration over autoconfiguration:

```kotlin
@Bean
fun vendorApiLogSink(repository: VendorApiLogRepository): VendorApiLogSink =
    JpaVendorApiLogSink(repository)

@Bean
fun vendorApiLogQuery(repository: VendorApiLogRepository): VendorApiLogQuery =
    JpaVendorApiLogQuery(repository)
```

---

## See also

- [`vendorClient`](../vendorClient/README.md) — core module and builder API
- [`vendorClient-apiconfig-jpa`](../vendorClient-apiconfig-jpa/README.md) — JPA-backed config store
- [`vendorClient-ratelimiter-redis`](../vendorClient-ratelimiter-redis/README.md) — Redis rate limiter
