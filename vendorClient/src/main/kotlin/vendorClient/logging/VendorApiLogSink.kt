package vendorClient.logging

/**
 * Persistence port for vendor API logs.
 *
 * Declared as a `fun interface` so test doubles can be supplied as lambdas
 * (e.g. `VendorApiLogSink { logs += it }`).
 */
fun interface VendorApiLogSink {
    fun save(log: VendorApiLog)
}
