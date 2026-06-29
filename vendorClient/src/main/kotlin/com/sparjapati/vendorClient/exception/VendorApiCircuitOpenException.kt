package com.sparjapati.vendorClient.exception

import com.sparjapati.vendorClient.VendorApiKey

class VendorApiCircuitOpenException(api: VendorApiKey) : RuntimeException("Circuit breaker is open for ${api.name}")
