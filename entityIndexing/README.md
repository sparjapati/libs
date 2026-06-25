# EntityIndexing

A Spring Boot library that provides annotation-driven, reindex-on-write semantics for JPA entities. Annotate service methods to automatically collect modified entity IDs during a transaction, then convert and push them to a search store (e.g. Elasticsearch) after the transaction commits.

---

## Table of Contents

- [How It Works](#how-it-works)
- [Setup](#setup)
- [Usage Pattern](#usage-pattern)
  - [1. Define an index document](#1-define-an-index-document)
  - [2. Implement IndexConverter](#2-implement-indexconverter)
  - [3. Register an IndexSink](#3-register-an-indexsink)
  - [4. Annotate service methods](#4-annotate-service-methods)
- [Annotations](#annotations)
  - [@ReindexContext](#reindexcontext)
  - [@ReindexId](#reindexid)
- [IndexSink](#indexsink)
- [Internals](#internals)

---

## How It Works

```
@ReindexContext method called
    │
    ├── ReindexContextHolder.start()      (root call only — nested calls are no-ops)
    │
    ├── method executes
    │   └── marker method with @ReindexId param called
    │       └── ReindexParamAspect collects IDs into ThreadLocal context
    │
    └── method returns
        └── ReindexContextHolder.finish() → Map<KClass<*>, Set<String>>
            │
            ├── active transaction? → register afterCommit hook → reindex() per entity class
            └── no transaction?    → reindex() per entity class immediately
                                            │
                                    ReindexService.reindex()  (chunked JPA queries)
                                            │
                                    IndexSink.push()  (e.g. Elasticsearch)
```

If the surrounding transaction rolls back, the event is never published and no reindex occurs.

---

## Setup

Add the dependency:

```kotlin
// build.gradle.kts
implementation("com.sparjapati:entityIndexing:<version>")
```

Enable the library on your main application class:

```kotlin
@SpringBootApplication
@EnableEntityIndexing
class MyApplication
```

---

## Usage Pattern

### 1. Define an index document

Extend `AbstractEntityIndex` to describe the shape of the indexed document:

```kotlin
class UserIndex(
    id: String,
    var name: String,
    var email: String,
    lastModified: Instant,
    lastModifiedBy: String,
    isDeleted: Boolean = false,
) : AbstractEntityIndex(id, lastModified, lastModifiedBy, isDeleted)
```

If you are pushing to Elasticsearch, also add `@Document`:

```kotlin
@Document(indexName = "users")
class UserIndex(...)  : AbstractEntityIndex(...)
```

### 2. Implement IndexConverter

Declare which JPA entity class this converter handles via `entityClass`:

```kotlin
@Component
class UserIndexConverter : IndexConverter<UserEntity, UserIndex> {

    override val entityClass = UserEntity::class

    override fun convert(source: UserEntity): UserIndex =
        UserIndex(
            id           = source.id,
            name         = source.name,
            email        = source.email,
            lastModified = source.lastModified,
            lastModifiedBy = source.lastModifiedBy,
            isDeleted    = source.isDeleted,
        )
}
```

The library validates at startup that no two converters are registered for the same entity class.

### 3. Register an IndexSink

The library provides `ElasticsearchIndexSink` — register it as a bean in your application:

```kotlin
@Bean
fun elasticsearchIndexSink(ops: ElasticsearchOperations) = ElasticsearchIndexSink(ops)
```

Or implement the `IndexSink` interface to push to any other store. Multiple sinks are supported; all receive each batch.

### 4. (Optional) Register per-entity listeners

For logic that should run only for a specific entity class, implement `EntityIndexListener` instead of `IndexSink`:

```kotlin
@Component
class UserReindexListener : EntityIndexListener<UserIndex> {

    override val entityClass = UserEntity::class

    override fun onReindex(documents: List<UserIndex>) {
        // called only when UserEntity documents are reindexed
    }
}
```

Multiple listeners for the same entity class are all called. `IndexSink` and `EntityIndexListener` are independent — you can use both.

### 5. Annotate service methods

Mark the transaction boundary with `@ReindexContext`, create a marker method with `@ReindexId`, and call it with the IDs of the changed entities:

```kotlin
@Service
class UserService(
    @Lazy @Resource(type = UserService::class) private val self: UserService,
) {
    @Transactional
    @ReindexContext
    fun createUsers(requests: List<CreateUserRequest>): List<UserDto> {
        val saved = userRepository.saveAll(requests.map { it.toEntity() })
        self.reindexUsers(userIds = saved.map { it.id })
        return saved.map { it.toDto() }
    }

    // Empty body — exists only so @ReindexId can be intercepted by the aspect.
    fun reindexUsers(@ReindexId(UserEntity::class) userIds: List<String>) {}
}
```

The `self` reference is required because the marker method must be called through the Spring proxy for the aspect to intercept it.

---

## Annotations

### @ReindexContext

```kotlin
@ReindexContext
fun myServiceMethod(...) { ... }
```

Marks the root of a reindex scope. On exit (after transaction commit if active) it calls `ReindexService` + all `IndexSink` beans for each collected entity class.

The `propagation` parameter controls behaviour when called inside an existing scope:

| Propagation | Behaviour |
|---|---|
| `REQUIRED` *(default)* | Joins the active scope. Nested calls contribute their IDs to the outermost scope's batch. |
| `REQUIRES_NEW` | Always starts a fresh, independent scope. The outer scope is suspended until this one completes and flushes. |

```kotlin
// inner method flushes independently, outer scope resumes after
@ReindexContext(propagation = ReindexPropagation.REQUIRES_NEW)
fun reindexCriticalEntity(id: String) { ... }
```

### @ReindexId

```kotlin
fun markerMethod(@ReindexId(UserEntity::class) ids: List<String>) {}
```

Marks a method parameter (type `String` or `Collection<*>`) whose value(s) should be registered for reindexing. The aspect collects these values only when a `@ReindexContext` scope is active — calls outside a scope are ignored. The `entity` parameter is the JPA entity `KClass` that identifies which `IndexConverter` to use.

---

## IndexSink

```kotlin
interface IndexSink {
    fun push(entityClass: KClass<*>, documents: List<AbstractEntityIndex>)
}
```

Implement this interface and register the implementation as a Spring bean to receive converted index documents. The library calls all registered `IndexSink` beans for each reindex batch.

`ElasticsearchIndexSink` is provided as a ready-made implementation for Spring Data Elasticsearch. Concrete index document classes must carry `@Document(indexName = "...")` for it to resolve the target index.

---

## Internals

### Beans registered by `@EnableEntityIndexing`

| Bean | Role |
|---|---|
| `IndexConverterRegistry` | Discovers all `IndexConverter` beans; validates no duplicate entity class registrations at startup |
| `ReindexContextAspect` | `@Around` advice for `@ReindexContext`; manages `ReindexContextHolder` lifecycle; calls `ReindexService`, all `IndexSink` beans, and matching `EntityIndexListener` beans per entity class |
| `ReindexParamAspect` | `@AfterReturning` advice on all Spring beans; collects `@ReindexId`-annotated parameters into the active context |
| `ReindexService` | Loads entities from the database (chunked JPA Criteria queries) and converts them via `IndexConverterRegistry` |

No beans are registered unless `@EnableEntityIndexing` is present.

### Transaction safety and aspect ordering

`ReindexContextAspect` is annotated with `@Order(Ordered.LOWEST_PRECEDENCE - 1)`, placing it **just outside** Spring's `@Transactional` interceptor (which defaults to `Ordered.LOWEST_PRECEDENCE`). This makes the ordering deterministic regardless of bean registration order.

Because `ReindexContextAspect` is the outer wrapper, what `isActualTransactionActive()` returns in the `finally` block depends on the call context:

| Situation | `isActualTransactionActive()` in `finally` | Path taken |
|---|---|---|
| `@ReindexContext` + `@Transactional` on the same method | `false` — own transaction already committed | event published immediately |
| Method called from within an outer `@Transactional` | `true` — caller's transaction still open | event deferred to `afterCommit()` |
| `@ReindexContext` only, no transaction | `false` | event published immediately |

In all cases the event only reaches the listener after the relevant transaction has committed — either because it already committed before the `finally` block ran, or because the `afterCommit` hook waits for the outer commit.

If the transaction rolls back, `completed` is `false` (the exception propagated through `pjp.proceed()`) and the event is never published.

> **Note:** the order of `@Transactional` and `@ReindexContext` on a method declaration has no effect on AOP execution order — only the `@Order` value on the aspect class matters.

### ID deduplication

`ReindexContextHolder` stores IDs in a `MutableSet` per entity class. Multiple calls to the marker method within the same `@ReindexContext` scope automatically deduplicate — each ID is only reindexed once per scope.
