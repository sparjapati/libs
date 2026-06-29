package vendorClient.interceptor

import vendorClient.annotation.VendorApiAnnotationResolver
import vendorClient.config.VendorApiConfigProvider
import vendorClient.config.VendorApiResilienceConfig
import vendorClient.exception.VendorApiCircuitOpenException
import vendorClient.exception.VendorApiServerErrorException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.core.functions.CheckedSupplier
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.Duration

/**
 * Applies Resilience4j circuit breaker and retry to vendor API calls.
 *
 * CB and Retry instances are lazily built per [vendorClient.VendorApiKey.name]
 * and cached in their registries for the application lifetime. Config changes (thresholds, window
 * size, wait durations) require an app restart to take effect. The enabled flags are re-read on
 * every request — toggling them off is hot-reloadable.
 *
 * Each JVM instance maintains its own circuit breaker state independently.
 *
 * **Decoration order**: retry is applied first (inner), circuit breaker second (outer). This means
 * the CB counts each logical call (including all retries) as a single outcome — only the final
 * failure after all retry attempts are exhausted is recorded against the circuit breaker.
 */
class VendorApiResilienceInterceptor(
    private val getConfig: VendorApiConfigProvider,
) : Interceptor {

    private val cbRegistry = CircuitBreakerRegistry.ofDefaults()
    private val retryRegistry = RetryRegistry.ofDefaults()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val api = VendorApiAnnotationResolver.resolve(request)
            ?: return chain.proceed(request)
        val config = getConfig.getConfig(api)
            ?: return chain.proceed(request)
        val resilience = config.resilience

        // Base supplier: execute the request and convert 5xx responses into a retryable IOException
        // so both the retry and the circuit breaker treat them as failures.
        var supplier: CheckedSupplier<Response> = CheckedSupplier {
            val response = chain.proceed(request)
            if (response.code >= 500) {
                response.close()
                throw VendorApiServerErrorException(api, response.code)
            }
            response
        }

        // Retry wraps the base supplier (inner). After all attempts fail the last IOException
        // propagates out of the retry-decorated supplier unchanged.
        if (resilience.retryEnabled) {
            val retry = retryRegistry.retry(api.name, buildRetryConfig(resilience))
            supplier = retry.decorateCheckedSupplier(supplier)
        }

        // Circuit breaker wraps the (possibly retry-decorated) supplier (outer). The CB records a
        // single outcome for each call, which includes any retries that happened inside.
        if (resilience.cbEnabled) {
            val cb = cbRegistry.circuitBreaker(api.name, buildCbConfig(resilience))
            supplier = cb.decorateCheckedSupplier(supplier)
        }

        return try {
            supplier.get()
        } catch (e: CallNotPermittedException) {
            throw VendorApiCircuitOpenException(api)
        } catch (e: IOException) {
            throw e
        } catch (e: Throwable) {
            // Resilience4j may wrap checked exceptions in RuntimeException; unwrap if the
            // original cause is an IOException so callers see the expected exception type.
            val cause = e.cause
            if (cause is IOException) throw cause
            throw e
        }
    }

    private fun buildCbConfig(r: VendorApiResilienceConfig) = CircuitBreakerConfig.custom()
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(r.cbSlidingWindowSize)
        .failureRateThreshold(r.cbFailureRateThreshold.toFloat())
        .waitDurationInOpenState(Duration.ofSeconds(r.cbWaitDurationSeconds.toLong()))
        .permittedNumberOfCallsInHalfOpenState(1)
        .recordExceptions(IOException::class.java)
        .build()

    private fun buildRetryConfig(r: VendorApiResilienceConfig) = RetryConfig.custom<Any>()
        .maxAttempts(r.retryMaxAttempts)
        .intervalFunction(
            IntervalFunction.ofExponentialBackoff(
                r.retryInitialIntervalMs,
                r.retryMultiplier,
                r.retryMaxIntervalMs,
            )
        )
        .retryOnException { it is IOException }
        .build()
}
