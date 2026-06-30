# vendorClient

A Kotlin library for calling vendor HTTP APIs — with rate limiting, circuit breaking, retry,
trace forwarding, and structured logging. Every feature is opt-in. No hardcoded strings.

Built on **OkHttp 4.12 · Retrofit 2.11 · Resilience4j 2.3 · Kotlin 2**.  
The core module has **no runtime Spring, JPA, or Redis dependencies**.

---

## Installation

```kotlin
// build.gradle.kts
implementation("com.sparjapati:vendorClient:0.0.1")
```

---

## Quick Start

### 1. Define your API key

Implement `VendorApiKey` as an enum. Each constant maps to one rate-limit window, one circuit
breaker instance, and one config row.

```kotlin
enum class PaymentApi : VendorApiKey {
    CHARGE,
    REFUND;

    // Optional: override to use a different header name for this API's trace-id
    override val traceHeader: String? get() = "X-Stripe-Idempotency-Key"
}
```

### 2. Annotate your Retrofit interface

```kotlin
interface StripeService {

    @TraceableApi(api = PaymentApi::class, name = "CHARGE")
    @POST("v1/charges")
    fun createCharge(@Body body: ChargeRequest): Call<NetworkResponse<ChargeResponse>>
}
```

Methods without `@TraceableApi` are invisible to all vendor interceptors — they pass through
untouched with no rate limiting, circuit breaking, or logging.

### 3. Build the client

```kotlin
val stripe: StripeService = VendorClient.builder()
    .baseUrl("https://api.stripe.com/")
    .configProvider(myConfigProvider)
    .rateLimiter(store = InMemoryRateLimitStore(), onTempDisable = configManager::tempDisable)
    .resilience()
    .trace { requestContext.requestId() }
    .apiLogging(logSink)
    .build()
    .create(StripeService::class.java)
```

### 4. Consume the response

```kotlin
stripe.createCharge(body).execute().body()!!
    .onSuccess { charge -> println("Charged: ${charge.id}") }
    .onError   { err   ->
        when (err.exceptionType) {
            NetworkExceptionType.RATE_LIMITED   -> /* back off */
            NetworkExceptionType.CIRCUIT_OPEN   -> /* fail fast */
            NetworkExceptionType.API_DISABLED   -> /* config issue */
            NetworkExceptionType.NETWORK        -> /* retry upstream */
            else                                -> /* unexpected */
        }
    }
```

---

## Builder API

`baseUrl` and `configProvider` are the only required methods. The **interceptor order is always
fixed** regardless of the order you call builder methods:

```
RateLimit → Resilience → Trace → ApiLogging → HttpLogging
```

| Method | What it enables |
|---|---|
| `.baseUrl(url)` | **Required.** Retrofit base URL. |
| `.configProvider(provider)` | **Required.** Supplies `VendorApiConfig` per API. |
| `.rateLimiter(store, onTempDisable)` | Sliding-window rate limiting. `onTempDisable` called on breach — wire `configManager::tempDisable`. |
| `.resilience()` | Resilience4j circuit breaker + exponential-backoff retry. Config driven by `VendorApiResilienceConfig`. |
| `.trace(requestIdProvider)` | Forwards the inbound request-id to each outbound attempt with a per-attempt suffix. |
| `.apiLogging(sink)` | Persists a structured `VendorApiLog` per attempt via `VendorApiLogSink`. |
| `.httpLogging(level, sink)` | Raw HTTP traffic log. Defaults to `Level.BODY` via SLF4J. |
| `.settings(settings)` | Override `VendorClientSettings` defaults (header names, timeouts, sensitive headers). |
| `.customizeOkHttp(block)` | Escape hatch for custom OkHttp config (SSL, interceptors). Runs after all library interceptors. |

---

## Features

### Rate Limiting

Tokens consumed **once per logical request** (before retries). On breach the API enters a
temporary cooldown and `onTempDisable` is called with the expiry instant.

`InMemoryRateLimitStore` is included for development and single-node use. For multi-instance
deployments, use `vendorClient-ratelimiter-redis` which provides `RedisRateLimitStore` backed by a Lua
atomic sliding window.

> **Multi-instance note:** Each JVM maintains an independent window. With _N_ instances the
> effective limit is _N × maxRequests_. Use `RedisRateLimitStore` for shared enforcement.

### Resilience (Circuit Breaker + Retry)

Configure per API via `VendorApiResilienceConfig` inside `VendorApiConfig`:

| Field | Default | Meaning |
|---|---|---|
| `cbEnabled` | `false` | Opt-in circuit breaker |
| `cbFailureRateThreshold` | `50` | % failures to open the CB |
| `cbSlidingWindowSize` | `10` | Call count in the rolling window |
| `cbWaitDurationSeconds` | `30` | Seconds CB stays OPEN before HALF_OPEN probe |
| `retryEnabled` | `false` | Opt-in retry |
| `retryMaxAttempts` | `3` | Total attempts (1 original + N-1 retries) |
| `retryInitialIntervalMs` | `500` | First backoff delay (ms) |
| `retryMultiplier` | `2.0` | Exponential multiplier |
| `retryMaxIntervalMs` | `10000` | Backoff cap (ms) |

> **Config lifecycle:** CB and Retry instances are cached for the application lifetime. Tuning
> parameters require an app restart. The `cbEnabled` / `retryEnabled` flags are re-read on every
> request and are hot-reloadable.

5xx responses are treated as failures for both retry and circuit breaker. 4xx pass through unchanged.

### Trace Forwarding

`TraceForwardingInterceptor` stamps `{requestId}-{8chars}` on each outbound attempt.
The `requestId` comes from the `requestIdProvider` lambda you supply — typically your inbound
Spring request ID from a context holder.

Per-API override: set `traceHeader` on your `VendorApiKey` constant to use a different header
name for that specific API (e.g. `X-Stripe-Idempotency-Key` instead of the global default).

### Structured API Logging

`VendorApiLoggingInterceptor` saves a `VendorApiLog` per attempt via `VendorApiLogSink`:

- Full request and response body (no size limit)
- Raw, unmasked headers (the sink receives full fidelity for storage)
- Binary content-types → saved as `BINARY_BODY_PLACEHOLDER` instead of garbled bytes
- `requestId` is the original inbound request ID (not the per-attempt trace ID)
- Duration measured via `kotlin.time.TimeSource`

### HTTP Debug Logging

`HttpLoggingInterceptor` prints raw HTTP traffic. Three levels: `NONE`, `HEADERS`, `BODY`.

Sensitive headers are **masked** in the log output (value replaced with `***`, name preserved).
Default sensitive headers: `authorization`, `x-api-key`, `api-key`, `proxy-authorization`,
`cookie`, `set-cookie`, `x-auth-token`, `x-access-token`, `token`.  
Override via `VendorClientSettings.sensitiveHeaders`.

Binary response bodies are logged as `BINARY_BODY_PLACEHOLDER`.

---

## Configuration

### VendorClientSettings

```kotlin
VendorClientSettings(
    requestIdHeader       = "X-Request-Id",   // forwarded on outbound calls
    rateLimiterKeyPrefix  = "vendorApiRate",   // Redis key prefix (vendorClient-redis)
    sensitiveHeaders      = DEFAULT_SENSITIVE_HEADERS,
    connectTimeoutSeconds = 30,
    readTimeoutSeconds    = 30,
    writeTimeoutSeconds   = 30,
)
```

### Spring Boot Autoconfiguration

When `spring-boot-autoconfigure` is on the classpath, a `VendorClientSettings` bean is
registered automatically from `application.yml`:

```yaml
vendor-client:
  request-id-header: X-Request-Id
  rate-limiter-key-prefix: vendorApiRate
  connect-timeout-seconds: 30
  read-timeout-seconds: 30
  write-timeout-seconds: 30
  # sensitive-headers: [authorization, x-api-key]  # omit to use defaults
```

Declare your own `VendorClientSettings` bean to opt out (`@ConditionalOnMissingBean`).

---

## Extension Points

Implement these interfaces to plug in persistence or custom stores:

| Interface | Responsibility |
|---|---|
| `VendorApiConfigProvider` | Supply `VendorApiConfig` per API at request time |
| `VendorApiConfigManager` | Create, update, and temp-disable API configs |
| `RateLimitStore` | Backend for the sliding-window token counter |
| `VendorApiLogSink` | Persist `VendorApiLog` entries (DB, Elasticsearch, etc.) |
| `VendorApiLogQuery` | Read log entries by requestId prefix or API name |
| `LogSink` | Output sink for `HttpLoggingInterceptor` (defaults to SLF4J) |

---

## Modules

All modules self-register via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
— no `@Enable*` annotation needed in the host application.

| Artifact | Package | Contents |
|---|---|---|
| `vendorClient` | `vendorClient.*` | Core — interceptors, Retrofit adapter, builder, in-memory rate limiter, Spring autoconfiguration |
| `vendorClient-apiconfig-jpa` | `vendorClient.apiconfig.jpa.*` | `JpaVendorApiConfigProvider`, `JpaVendorApiConfigManager` — JPA-backed config store |
| `vendorClient-apilog-jpa` | `vendorClient.apilog.jpa.*` | `JpaVendorApiLogSink`, `JpaVendorApiLogQuery` — JPA-backed structured log persistence |
| `vendorClient-ratelimiter-redis` | `vendorClient.ratelimiter.redis.*` | `RedisRateLimitStore` — Lua atomic sliding-window rate limiter backed by Redis |

### Typical dependency set

```kotlin
// build.gradle.kts
implementation("com.sparjapati:vendorClient:0.0.1")
implementation("com.sparjapati:vendorClient-apiconfig-jpa:0.0.1")   // if using JPA config
implementation("com.sparjapati:vendorClient-apilog-jpa:0.0.1")      // if using JPA log persistence
implementation("com.sparjapati:vendorClient-ratelimiter-redis:0.0.1") // if using Redis rate limiter
```

Modules are independently consumable — include only what you need.

---

## NetworkResponse

All Retrofit calls return `Call<NetworkResponse<T>>`. The sealed type carries either the typed
body (`Success`) or a typed error reason (`Error`).

```kotlin
sealed class NetworkResponse<out T> {
    data class Success<T>(val data: T, ...)  : NetworkResponse<T>()
    data class Error(val exceptionType: NetworkExceptionType, ...) : NetworkResponse<Nothing>()
}
```

| `NetworkExceptionType` | Cause |
|---|---|
| `HTTP` | Non-2xx response |
| `NETWORK` | IOException |
| `RATE_LIMITED` | Local rate limit exceeded |
| `API_DISABLED` | API permanently disabled in config |
| `TEMP_API_DISABLED` | API in cooldown window |
| `CIRCUIT_OPEN` | Circuit breaker OPEN |
| `UNEXPECTED` | Any other throwable |

Convenience operators: `map`, `fold`, `getOrElse`, `onSuccess`, `onError`.
