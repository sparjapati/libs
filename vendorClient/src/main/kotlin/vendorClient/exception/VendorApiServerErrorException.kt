package vendorClient.exception

import vendorClient.VendorApiKey
import java.io.IOException

// internal — never propagates past the resilience interceptor.
// Extends IOException (not RuntimeException) so the OkHttp/Retrofit resilience interceptor
// can catch it via the standard IOException handler that also triggers circuit-breaker and retry logic.
internal class VendorApiServerErrorException(api: VendorApiKey, code: Int) : IOException("HTTP $code from ${api.name}")
