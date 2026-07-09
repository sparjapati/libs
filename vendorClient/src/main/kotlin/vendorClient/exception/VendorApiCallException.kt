package vendorClient.exception

import vendorClient.response.NetworkResponse

/** Thrown by [vendorClient.retrofit.executeOrThrow] when the call resulted in a [NetworkResponse.Error]. */
class VendorApiCallException(val error: NetworkResponse.Error) : RuntimeException(error.message)
