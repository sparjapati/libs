package vendorClient.interceptor

import vendorClient.VendorApiKey
import vendorClient.annotation.VendorApiAnnotationResolver
import vendorClient.config.VendorApiConfigProvider
import vendorClient.exception.VendorApiDisabledException
import vendorClient.exception.VendorApiRateLimitExceededException
import vendorClient.exception.VendorApiTemporarilyDisabledException
import vendorClient.ratelimit.RateLimitStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that enforces per-API rate limits and enabled/disabled state before
 * allowing an outbound request to proceed.
 *
 * **Pass-through cases** (no rate-limit check performed):
 * - The request's Retrofit [Invocation] has no [@TraceableApi][vendorClient.annotation.TraceableApi]
 *   annotation — the interceptor cannot identify the API.
 * - [getConfig] returns null for the resolved [VendorApiKey] — no configuration is registered for this API.
 *
 * **Guard cases** (exception thrown before the request is forwarded):
 * - [vendorClient.config.VendorApiConfig.enabled] is false →
 *   [VendorApiDisabledException]
 * - [vendorClient.config.VendorApiConfig.isTemporarilyDisabled] is true →
 *   [VendorApiTemporarilyDisabledException]
 * - [RateLimitStore.tryAcquire] returns false (token budget exhausted) →
 *   [onTempDisable] is invoked, then [VendorApiRateLimitExceededException] is thrown.
 *
 * @param getConfig supplies the current [vendorClient.config.VendorApiConfig] for a given [VendorApiKey].
 * @param rateLimitStore sliding-window token store; must be safe to call from multiple threads.
 * @param onTempDisable callback invoked with the affected [VendorApiKey] and the computed disable-until
 *   epoch millis when the rate limit is exceeded. Matches the signature of
 *   [vendorClient.config.VendorApiConfigManager.tempDisable] so callers can pass it as a method reference.
 */
class VendorApiRateLimitInterceptor(
    private val getConfig: VendorApiConfigProvider,
    private val rateLimitStore: RateLimitStore,
    private val onTempDisable: (VendorApiKey, Long) -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val api = VendorApiAnnotationResolver.resolve(request)
            ?: return chain.proceed(request)

        val config = getConfig.getConfig(api)
            ?: return chain.proceed(request)

        if (!config.enabled) throw VendorApiDisabledException(api)

        val now = System.currentTimeMillis()
        if (config.isTemporarilyDisabled(now)) throw VendorApiTemporarilyDisabledException(api)

        if (!rateLimitStore.tryAcquire(api, config.maxRequests, config.windowSeconds)) {
            val until = now + config.windowSeconds * 1000L
            onTempDisable(api, until)
            throw VendorApiRateLimitExceededException("Rate limit exceeded for ${api.name}")
        }

        return chain.proceed(request)
    }
}
