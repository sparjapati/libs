package com.sparjapati.vendorClient.exception

import com.sparjapati.vendorClient.VendorApiKey

class VendorApiTemporarilyDisabledException(api: VendorApiKey) : RuntimeException("API temporarily disabled: ${api.name}")
