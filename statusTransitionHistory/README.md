# statusTransitionHistory

An append-only status-transition history log for domain entities — record every time an entity
(e.g. an `Order`) moves from one status to another, and query that history back.

---

## Setup

```kotlin
// build.gradle.kts
implementation("com.sparjapati:statusTransitionHistory:0.0.1")
```

`statusTransitionHistory` only defines the [`StatusTransitionRecord`](src/main/kotlin/com/statusTransitionHistory/history/StatusTransitionRecord.kt)
model and the [`StatusTransitionStore`](src/main/kotlin/com/statusTransitionHistory/history/StatusTransitionStore.kt)
interface. Add a store adapter module (e.g. [`statusTransitionHistory-mysql`](../statusTransitionHistory-mysql/README.md))
for durable, queryable storage.

---

## `StatusTransitionRecord`

| Field | Type | Description |
|---|---|---|
| `id` | `Long?` | This row's own generated identifier; `null` before persistence |
| `entity` | `String` | Name of the entity type that transitioned, e.g. `"Order"` |
| `entityId` | `String` | The entity's own id, as a String — generic across Long/UUID/String-keyed entities |
| `fromStatus` | `String` | Status before the transition |
| `toStatus` | `String` | Status after the transition |
| `comment` | `String` | Human-readable note; defaults to a generated message (see below) |
| `transitionedAt` | `Long` | Epoch millis when the transition happened |

### Default comment

`StatusTransitionRecord.forTransition(...)` and `StatusTransitionStore.record(...)` both accept an
optional `comment`. When omitted, it defaults to:

```
status updated from $fromStatus to $toStatus
```

e.g. `"status updated from PENDING to PAID"`.

---

## `StatusTransitionStore`

| Method | Returns | Notes |
|---|---|---|
| `record(entity, entityId, fromStatus, toStatus, comment?)` | `StatusTransitionRecord` | Always inserts a new row — never updates an existing one |
| `findAll(entity, entityId, pageable)` | `Page<StatusTransitionRecord>` | This entity instance's transition history, most recent first |

### Choosing a storage backend

`StatusTransitionStore` has no no-op or in-memory default implementation. Add a store adapter
module (e.g. [`statusTransitionHistory-mysql`](../statusTransitionHistory-mysql/README.md)) to the
classpath so a `StatusTransitionStore` bean is available; without one, injecting
`StatusTransitionStore` fails at application startup.
