package com.sparjapati.vendorClient.exception

import com.sparjapati.vendorClient.VendorApiKey

class VendorApiDisabledException(api: VendorApiKey) : RuntimeException("API disabled: ${api.name}")
