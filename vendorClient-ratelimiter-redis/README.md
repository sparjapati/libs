# vendorClient-ratelimiter-redis

Redis-backed sliding-window rate limiter for `vendorClient` — safe for multi-node deployments.

Implements [`RateLimitStore`](../vendorClient/src/main/kotlin/vendorClient/ratelimit/RateLimitStore.kt)
via a Lua script that atomically checks and increments a Redis sorted set, guaranteeing
race-free enforcement across multiple JVM instances.

---

## Installation

```kotlin
// build.gradle.kts
implementation("com.sparjapati:vendorClient:0.0.1")
implementation("com.sparjapati:vendorClient-ratelimiter-redis:0.0.1")
```

Spring Boot autoconfiguration registers `RedisRateLimitStore` automatically — no `@Enable*`
annotation or explicit bean declaration required.

---

## What gets auto-configured

| Bean | Interface | Purpose |
|---|---|---|
| `RedisRateLimitStore` | `RateLimitStore` | Shared sliding-window counter backed by Redis |

The bean is `@ConditionalOnMissingBean(RateLimitStore::class)` — declare your own `RateLimitStore`
to opt out.

**Requires** a `RedisCommands<String, String>` (Lettuce synchronous commands) bean in the
application context. Spring Boot's `spring-boot-starter-data-redis` provides this automatically
when `spring.data.redis.host` is configured.

---

## How it works

Each API key maps to a Redis sorted set: `{keyPrefix}:{api.name}`.

On every `tryAcquire` call a single Lua script runs atomically:

1. Removes members older than the window (`ZREMRANGEBYSCORE`).
2. Counts the remaining members (`ZCARD`).
3. If `count < maxRequests`: adds the current timestamp as a new member, sets a TTL, returns `1` (allowed).
4. Otherwise returns `0` (denied).

Because Lua execution is atomic in Redis, there is no race between the check and the increment —
unlike the in-process `InMemoryRateLimitStore` which allows up to `N × maxRequests` across
`N` JVM instances.

The Lua script is uploaded to Redis once via `SCRIPT LOAD` at startup. Subsequent calls use
`EVALSHA` with the returned SHA, which is cheaper than re-sending the full script.

---

## Configuration

The key prefix defaults to `"vendorApiRate"` and can be overridden via `VendorClientSettings`:

```yaml
# application.yml
vendor-client:
  rate-limiter-key-prefix: myApp:vendorRate
```

Or by declaring a `VendorClientSettings` bean directly:

```kotlin
@Bean
fun vendorClientSettings() = VendorClientSettings(rateLimiterKeyPrefix = "myApp:vendorRate")
```

Redis key format: `{rateLimiterKeyPrefix}:{VendorApiKey.name}`  
Example: `vendorApiRate:CHARGE`

---

## Manual bean setup

If you prefer explicit configuration over autoconfiguration:

```kotlin
@Bean
fun rateLimitStore(redisCommands: RedisCommands<String, String>): RateLimitStore {
    val sha = redisCommands.scriptLoad(RedisRateLimitStore.LUA_SLIDING_WINDOW_SCRIPT)
    return RedisRateLimitStore(
        redisCommands = redisCommands,
        scriptSha = sha,
        keyPrefix = "myApp:vendorRate",
    )
}
```

`LUA_SLIDING_WINDOW_SCRIPT` is a public constant on `RedisRateLimitStore` — load it once and
pass the SHA to avoid re-sending the full script on every connection.

---

## Single-node alternative

`vendorClient` bundles `InMemoryRateLimitStore` for development and single-node deployments.
It requires no external dependencies but does not coordinate across JVM instances.

---

## See also

- [`vendorClient`](../vendorClient/README.md) — core module and builder API
- [`vendorClient-apiconfig-jpa`](../vendorClient-apiconfig-jpa/README.md) — JPA-backed config store
- [`vendorClient-apilog-jpa`](../vendorClient-apilog-jpa/README.md) — JPA-backed log persistence
