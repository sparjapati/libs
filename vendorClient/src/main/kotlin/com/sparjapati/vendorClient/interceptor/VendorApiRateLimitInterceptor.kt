package com.sparjapati.vendorClient.interceptor

import com.sparjapati.vendorClient.VendorApiKey
import com.sparjapati.vendorClient.annotation.VendorApiAnnotationResolver
import com.sparjapati.vendorClient.config.VendorApiConfigProvider
import com.sparjapati.vendorClient.exception.VendorApiDisabledException
import com.sparjapati.vendorClient.exception.VendorApiRateLimitExceededException
import com.sparjapati.vendorClient.exception.VendorApiTemporarilyDisabledException
import com.sparjapati.vendorClient.ratelimit.RateLimitStore
import okhttp3.Interceptor
import okhttp3.Response
import java.time.Instant

/**
 * OkHttp interceptor that enforces per-API rate limits and enabled/disabled state before
 * allowing an outbound request to proceed.
 *
 * **Pass-through cases** (no rate-limit check performed):
 * - The request's Retrofit [Invocation] has no [@TraceableApi][com.sparjapati.vendorClient.annotation.TraceableApi]
 *   annotation — the interceptor cannot identify the API.
 * - [getConfig] returns null for the resolved [VendorApiKey] — no configuration is registered for this API.
 *
 * **Guard cases** (exception thrown before the request is forwarded):
 * - [com.sparjapati.vendorClient.config.VendorApiConfig.enabled] is false →
 *   [VendorApiDisabledException]
 * - [com.sparjapati.vendorClient.config.VendorApiConfig.isTemporarilyDisabled] is true →
 *   [VendorApiTemporarilyDisabledException]
 * - [RateLimitStore.tryAcquire] returns false (token budget exhausted) →
 *   [onTempDisable] is invoked, then [VendorApiRateLimitExceededException] is thrown.
 *
 * @param getConfig supplies the current [com.sparjapati.vendorClient.config.VendorApiConfig] for a given [VendorApiKey].
 * @param rateLimitStore sliding-window token store; must be safe to call from multiple threads.
 * @param onTempDisable callback invoked with the affected [VendorApiKey] and the computed disable-until [Instant]
 *   when the rate limit is exceeded. Matches the signature of
 *   [com.sparjapati.vendorClient.config.VendorApiConfigManager.tempDisable] so callers can pass it as a method reference.
 */
class VendorApiRateLimitInterceptor(
    private val getConfig: VendorApiConfigProvider,
    private val rateLimitStore: RateLimitStore,
    private val onTempDisable: (VendorApiKey, Instant) -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val api = VendorApiAnnotationResolver.resolve(request)
            ?: return chain.proceed(request)

        val config = getConfig.getConfig(api)
            ?: return chain.proceed(request)

        if (!config.enabled) throw VendorApiDisabledException(api)

        val now = Instant.now()
        if (config.isTemporarilyDisabled(now)) throw VendorApiTemporarilyDisabledException(api)

        if (!rateLimitStore.tryAcquire(api, config.maxRequests, config.windowSeconds)) {
            val until = now.plusSeconds(config.windowSeconds.toLong())
            onTempDisable(api, until)
            throw VendorApiRateLimitExceededException("Rate limit exceeded for ${api.name}")
        }

        return chain.proceed(request)
    }
}
