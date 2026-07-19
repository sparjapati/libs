# idempotency-redis

Redis-backed `IdempotencyStore` adapter for `idempotency`, using Lettuce.

---

## Installation

```kotlin
runtimeOnly("com.sparjapati:idempotency-redis:0.0.1")
```

Requires a `RedisCommands<String, String>` bean already in your application context.

---

## Configuration

```yaml
idempotency:
  redis:
    key-prefix: idempotency   # default
```

---

## Storage shape

Each idempotency key is stored as one JSON string at `{key-prefix}:{key}`, with a TTL matching
either the issue TTL (while unused) or the post-claim retention TTL (once claimed). `claim` uses
a Lua script (Redis's built-in `cjson`) to atomically check-and-transition an ISSUED record to
IN_PROGRESS, so two concurrent first-uses of the same key can't both win.
