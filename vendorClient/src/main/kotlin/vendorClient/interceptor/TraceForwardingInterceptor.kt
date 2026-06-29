package vendorClient.interceptor

import vendorClient.annotation.VendorApiAnnotationResolver
import vendorClient.config.VendorClientSettings
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * Forwards the current request trace ID to outbound vendor API calls by injecting it as a header.
 *
 * The header name is resolved per-API via [vendorClient.VendorApiKey.traceHeader],
 * falling back to [VendorClientSettings.requestIdHeader] when the API has no override or when
 * the request method has no [@TraceableApi][vendorClient.annotation.TraceableApi]
 * annotation.
 *
 * The value written is `"$requestId-${8-char UUID suffix}"` so each retry attempt carries a
 * unique attempt ID while still being traceable back to the originating request.
 *
 * If [requestIdProvider] returns null (no active request context), the request is forwarded
 * unchanged — no header is injected.
 *
 * @param settings global settings supplying the fallback header name
 * @param requestIdProvider caller-supplied lambda that reads the current request ID
 *   (e.g. `{ AaiseHiContext.getRequestContext()?.requestId }`)
 */
class TraceForwardingInterceptor(
    private val settings: VendorClientSettings,
    private val requestIdProvider: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestId = requestIdProvider() ?: return chain.proceed(chain.request())
        val attemptSuffix = UUID.randomUUID().toString().take(8)
        val attemptId = "$requestId-$attemptSuffix"

        val api = VendorApiAnnotationResolver.resolve(chain.request())
        // Use per-API override when defined; fall back to the global setting for unannotated methods.
        val headerName = api?.traceHeader ?: settings.requestIdHeader

        val request = chain.request().newBuilder()
            .header(headerName, attemptId)
            .build()
        return chain.proceed(request)
    }
}
