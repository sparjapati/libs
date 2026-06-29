package vendorClient.logging

/**
 * Read interface for querying stored vendor API logs.
 *
 * Implementations live in adapter modules (e.g. `vendorClient-jpa`) so the core library
 * remains persistence-agnostic.
 */
interface VendorApiLogQuery {
    /**
     * Returns all log entries whose [VendorApiLog.requestId] starts with [requestIdPrefix].
     * Use the inbound request-id to group all retry attempts for one logical request.
     */
    fun findByRequestIdPrefix(requestIdPrefix: String): List<VendorApiLog>

    /** Paginated logs for [apiName], ordered by time descending. [page] is 0-indexed. */
    fun findByApiName(apiName: String, page: Int, pageSize: Int): VendorApiLogPage
}
