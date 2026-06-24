# Entity Lookup

A Spring Boot library for declarative entity existence validation in REST controllers. Annotate a method parameter with `@Entity("name")` and the library verifies the entity exists before the controller body runs — throwing `EntityNotFoundException` if it does not.

---

## Table of Contents

- [How It Works](#how-it-works)
- [Setup](#setup)
- [Implementing a Lookup Service](#implementing-a-lookup-service)
- [Annotating Controller Parameters](#annotating-controller-parameters)
- [EntityNotFoundException](#entitynotfoundexception)
- [Request-Scoped Cache](#request-scoped-cache)
- [Fail-Open Behaviour](#fail-open-behaviour)
- [Internals](#internals)

---

## How It Works

```
HTTP request → @RestController method call
    │
    └── EntityValidationAspect intercepts
            │
            ├── for each parameter annotated with @Entity("name"):
            │       │
            │       ├── check request-scoped cache (ENTITY_NAME:id)
            │       │       ├── cached false → throw EntityNotFoundException immediately
            │       │       └── cached true  → skip lookup
            │       │
            │       └── not cached → EntityLookupRegistry.exists(entityName, id)
            │               │
            │               ├── no service registered → pass (log warning)
            │               ├── exists = false → throw EntityNotFoundException
            │               └── exists = true  → cache true, continue
            │
            └── proceed to controller method
```

---

## Setup

Add the dependency:

```kotlin
// build.gradle.kts
implementation("com.sparjapati:entityLookup:<version>")
```

Enable the library on your main application class or any `@Configuration` class:

```kotlin
@SpringBootApplication
@EnableEntityValidation
class MyApplication
```

`@EnableEntityValidation` activates `EntityValidationConfiguration`, which registers:

| Bean | Role |
|---|---|
| `EntityLookupRegistry` | Maps entity names → `EntityLookupService` implementations |
| `EntityValidationAspect` | AOP advice that intercepts all `@RestController` methods |
| `EntityValidationCache` | Request-scoped cache for validation results (one instance per HTTP request) |

No beans are registered unless `@EnableEntityValidation` is present.

---

## Implementing a Lookup Service

Create a `@Component` implementing `EntityLookupService` for each entity type you want to validate.

```kotlin
@Component
class UserLookupService(
    private val userRepository: UserRepository,
) : EntityLookupService {

    override fun getEntity() = "USER"

    override fun exists(id: Any): Boolean =
        userRepository.existsById(id.toString())
}
```

```kotlin
@Component
class OrderLookupService(
    private val orderRepository: OrderRepository,
) : EntityLookupService {

    override fun getEntity() = "ORDER"

    override fun exists(id: Any): Boolean =
        orderRepository.existsById(id.toLong())
}
```

- `getEntity()` returns the entity name this service handles. Names are matched case-insensitively.
- `exists(id)` receives the raw parameter value as `Any` — cast to the required type inside the implementation.
- Register as many implementations as needed; all are discovered automatically at startup.

---

## Annotating Controller Parameters

Add `@Entity("name")` to any parameter in a `@RestController` method. The name must match the value returned by the corresponding `EntityLookupService.getEntity()` (case-insensitive).

```kotlin
@RestController
@RequestMapping("/orders")
class OrderController {

    @GetMapping("/{orderId}/items/{itemId}")
    fun getItem(
        @PathVariable @Entity("ORDER") orderId: Long,
        @PathVariable @Entity("ITEM")  itemId: Long,
    ): ItemResponse {
        // Reached only if both ORDER and ITEM exist
        return itemService.get(orderId, itemId)
    }

    @PostMapping("/{orderId}/notes")
    fun addNote(
        @PathVariable @Entity("ORDER") orderId: Long,
        @RequestBody  note: NoteRequest,
    ): NoteResponse {
        return noteService.add(orderId, note)
    }
}
```

Parameters without `@Entity` are not affected. Only `@RestController` methods are intercepted — `@Controller` methods are not.

---

## EntityNotFoundException

Thrown when a validated entity is not found:

```
No USER found for id: u-999
```

The message format is: `No {ENTITY_NAME} found for id: {id}`

Handle it in a `@ControllerAdvice` to return a consistent HTTP response:

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(ex: EntityNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(message = ex.message ?: "Entity not found"))
}
```

---

## Request-Scoped Cache

`EntityValidationCache` is request-scoped — a new instance is created for each HTTP request and discarded afterward.

Within a single request, each `ENTITY_NAME:id` pair is looked up at most once. If the same `orderId` appears in two parameters of the same method (or across nested intercepted calls within the same request), only one DB query is made.

The cache stores both `true` (exists) and `false` (not found) results. A cached `false` causes an immediate `EntityNotFoundException` without re-querying the DB.

---

## Fail-Open Behaviour

If no `EntityLookupService` is registered for a given entity name, the check **passes** and execution continues. A warning is logged:

```
No entity-lookup service found for entity ORDER
```

This prevents the library from blocking requests for entities that intentionally have no lookup registered. To enforce strict validation, ensure every entity name used in `@Entity(...)` has a corresponding `EntityLookupService` implementation.

---

## Internals

### Aspect pointcut

The aspect intercepts all methods within classes annotated with `@RestController`:

```kotlin
@Around("within(@org.springframework.web.bind.annotation.RestController *)")
```

It inspects each method parameter for the `@Entity` annotation via reflection. Parameters without the annotation are skipped entirely.

### Entity name matching

Names are normalised to uppercase in both the annotation (`annotation.name.uppercase()`) and the registry (`services.associateBy { it.getEntity().uppercase() }`). `@Entity("user")`, `@Entity("User")`, and `@Entity("USER")` all match a service returning `"user"` from `getEntity()`.

### Beans registered by `@EnableEntityValidation`

| Bean | Scope | Role |
|---|---|---|
| `EntityLookupRegistry` | Singleton | Maps entity name → `EntityLookupService` |
| `EntityValidationAspect` | Singleton | Intercepts `@RestController` methods |
| `EntityValidationCache` | Request | Caches validation results per HTTP request |
