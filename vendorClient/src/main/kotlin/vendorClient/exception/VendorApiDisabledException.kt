package vendorClient.exception

import vendorClient.VendorApiKey

class VendorApiDisabledException(api: VendorApiKey) : RuntimeException("API disabled: ${api.name}")
