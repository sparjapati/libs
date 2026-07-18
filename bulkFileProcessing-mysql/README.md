# bulkFileProcessing-mysql

JPA-backed `BulkJobStore` adapter for [`bulkFileProcessing`](../bulkFileProcessing/README.md) —
persists job records to MySQL via Spring Data JPA so they survive restarts and are visible
across instances.

Implements [`BulkJobStore`](../bulkFileProcessing/src/main/kotlin/com/bulkFileProcessing/jobstore/BulkJobStore.kt).

---

## Installation

```kotlin
// build.gradle.kts
implementation("com.sparjapati:bulkFileProcessing:0.0.3")
runtimeOnly("com.sparjapati:bulkFileProcessing-mysql:0.0.1")
```

`bulkFileProcessing-mysql` never needs to be referenced by class name — the app only needs it
on the classpath so its Spring Boot autoconfiguration registers the `mysqlBulkJobStore` bean,
which then wins automatically over the library's no-op/in-memory defaults (see
[bulkFileProcessing's README](../bulkFileProcessing/README.md#choosing-a-storage-backend)).
Inject `BulkJobStore` as normal — no adapter-specific type or qualifier needed.

---

## What gets auto-configured

| Bean | Type | Purpose |
|---|---|---|
| `mysqlBulkJobStore` | `BulkJobStore` | JPA-backed storage for bulk job records |

The repository and entity are scanned automatically; no `@EntityScan` or `@EnableJpaRepositories`
is needed in the host application for these classes.

---

## Database schema

Table: **`bulk_job_record`**

| Column | Type | Notes |
|---|---|---|
| `jobId` | `VARCHAR(255)` | Primary key |
| `processorType` | `VARCHAR(255)` NOT NULL | |
| `status` | `VARCHAR(255)` NOT NULL | Spring Batch `BatchStatus` name, e.g. `STARTED`, `COMPLETED`, `FAILED` |
| `writeCount` | `BIGINT` NOT NULL | |
| `skipCount` | `BIGINT` NOT NULL | |
| `resultFilePath` | `VARCHAR(255)` | Nullable |
| `errorMessage` | `TEXT` | Nullable; non-null only when `status = 'FAILED'` |
| `originalFileName` | `VARCHAR(255)` NOT NULL | |
| `startedAt` | `BIGINT` NOT NULL | Epoch millis |
| `completedAt` | `BIGINT` | Epoch millis; nullable while the job is still running |

Column names are camelCase, matching the entity's Kotlin properties exactly (not the
snake_case Hibernate would otherwise default to).

Indexed on `(processorType, status)` as `idx_bulk_job_record_processor_type_status` to support
`BulkJobStore.findAll` filtering.

A migration is not shipped by this library — apply the DDL in your own app's migrations.
