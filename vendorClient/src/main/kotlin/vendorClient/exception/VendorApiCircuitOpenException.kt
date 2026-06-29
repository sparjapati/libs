package vendorClient.exception

import vendorClient.VendorApiKey

class VendorApiCircuitOpenException(api: VendorApiKey) : RuntimeException("Circuit breaker is open for ${api.name}")
