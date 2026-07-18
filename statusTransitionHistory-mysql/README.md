# statusTransitionHistory-mysql

JPA-backed `StatusTransitionStore` adapter for [`statusTransitionHistory`](../statusTransitionHistory/README.md) —
persists status-transition records to MySQL via Spring Data JPA so they survive restarts and are
visible across instances.

Implements [`StatusTransitionStore`](../statusTransitionHistory/src/main/kotlin/com/statusTransitionHistory/history/StatusTransitionStore.kt).

---

## Installation

```kotlin
// build.gradle.kts
implementation("com.sparjapati:statusTransitionHistory:0.0.1")
runtimeOnly("com.sparjapati:statusTransitionHistory-mysql:0.0.1")
```

`statusTransitionHistory-mysql` never needs to be referenced by class name — the app only needs it
on the classpath so its Spring Boot autoconfiguration registers the `mysqlStatusTransitionStore`
bean. Inject `StatusTransitionStore` as normal — no adapter-specific type or qualifier needed.

---

## What gets auto-configured

| Bean | Type | Purpose |
|---|---|---|
| `mysqlStatusTransitionStore` | `StatusTransitionStore` | JPA-backed storage for status-transition records |

The repository and entity are scanned automatically; no `@EntityScan` or `@EnableJpaRepositories`
is needed in the host application for these classes.

---

## Database schema

Table: **`statusTransitionRecord`**

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` | Primary key, auto-increment |
| `entity` | `VARCHAR(255)` NOT NULL | Name of the entity type, e.g. `"Order"` |
| `entityId` | `VARCHAR(255)` NOT NULL | The entity's own id, as a String |
| `fromStatus` | `VARCHAR(255)` NOT NULL | |
| `toStatus` | `VARCHAR(255)` NOT NULL | |
| `comment` | `TEXT` NOT NULL | Defaults to `"status updated from $fromStatus to $toStatus"` when not supplied |
| `transitionedAt` | `BIGINT` NOT NULL | Epoch millis |

Column names are camelCase, matching the entity's Kotlin properties exactly (not the snake_case
Hibernate would otherwise default to).

Indexed on `(entity, entityId, transitionedAt)` as
`idx_status_transition_record_entity_entity_id_transitioned_at` to support
`StatusTransitionStore.findAll`'s equality lookup and most-recent-first ordering in a single scan.

A migration is not shipped by this library — apply the DDL in your own app's migrations.
