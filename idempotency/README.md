# idempotency

Server-issued idempotency keys for Spring Boot services. A consuming app asks this library to
issue a key scoped to a named operation, hands that key to its caller, and annotates the
operation's method with `@Idempotent` — the library validates the key on every call and either
runs the method once or replays its prior result.

---

## Installation

```kotlin
// build.gradle.kts
implementation("com.sparjapati:idempotency:0.0.1")

// Pick a storage adapter
runtimeOnly("com.sparjapati:idempotency-redis:0.0.1")
```

Core has no autoconfiguration of its own — it has no default backend. Add an adapter
(`idempotency-redis`) or implement `IdempotencyStore` yourself.

---

## Enabling

```kotlin
@SpringBootApplication
@EnableIdempotency(basePackages = ["com.myapp"])
class MyApplication
```

`basePackages` narrows which beans are proxied; leave it empty to fall back to matching the
`@Idempotent` annotation alone.

---

## Issuing a key

```kotlin
class OrderController(private val issuer: IdempotencyKeyIssuer) {
    @PostMapping("/orders/idempotency-key")
    fun issueKey(): Map<String, String> = mapOf("key" to issuer.issue(operation = "createOrder"))
}
```

Return the key to the caller however fits your API — a response body field, a response header,
whatever your contract is. The caller must send that exact key back on the configured header
(default `Idempotency-Key`) when it performs the operation.

---

## Annotating the operation

```kotlin
@Idempotent(operation = "createOrder")
fun createOrder(request: CreateOrderRequest): Order {
    ...
}
```

`operation` must match the name passed to `issuer.issue(...)` — a key issued for one operation
is rejected against any other.

- First call with a valid, unused key: the method runs; its result (or thrown exception) is
  recorded against the key.
- A repeat call with the same key and the same arguments: the recorded result/exception is
  replayed without running the method again.
- A repeat call with the same key but different arguments: rejected.
- A repeat call while the first call is still running: rejected.
- A call with a key that was never issued, or whose issued-but-unused TTL expired: rejected.

---

## Exceptions

| Exception | When | Suggested HTTP status |
|---|---|---|
| `MissingIdempotencyKeyException` | Header absent/blank | 400 |
| `UnknownIdempotencyKeyException` | Key never issued or expired | 404 |
| `IdempotencyOperationMismatchException` | Key issued for a different `operation` | 409 |
| `IdempotencyKeyReusedException` | Same key+operation, different arguments | 409 |
| `IdempotencyInProgressException` | Same key+operation+arguments, still running | 409 |
| `IdempotentOperationFailedException` | Replaying a recorded failure whose original exception type couldn't be reconstructed | map like the original failure would have been |

None of these are mapped to HTTP responses by this library — map them in your own
`@ExceptionHandler`.

---

## Configuration

```yaml
idempotency:
  header-name: Idempotency-Key   # default
  issue-ttl-seconds: 900          # default — how long an unused issued key stays valid
  default-ttl-seconds: 86400      # default — retention after first use, overridable per @Idempotent(ttlSeconds = ...)
```

---

## The extension point: `IdempotencyStore`

```kotlin
interface IdempotencyStore {
    fun issue(key: String, operation: String, ttlSeconds: Long)
    fun claim(key: String, operation: String, argsHash: String, ttlSeconds: Long): ClaimResult
    fun complete(key: String, operation: String, argsHash: String, response: String, ttlSeconds: Long)
    fun fail(key: String, operation: String, argsHash: String, exceptionClassName: String, exceptionMessage: String?, ttlSeconds: Long)
}
```

Implement this against any backend. `claim` must be atomic — see `idempotency-redis`'s
`RedisIdempotencyStore` for a Lua-script-based reference implementation.
