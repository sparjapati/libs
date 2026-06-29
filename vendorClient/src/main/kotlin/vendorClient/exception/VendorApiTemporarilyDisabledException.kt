package vendorClient.exception

import vendorClient.VendorApiKey

class VendorApiTemporarilyDisabledException(api: VendorApiKey) : RuntimeException("API temporarily disabled: ${api.name}")
