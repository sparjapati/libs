package com.sparjapati.vendorClient.logging

/**
 * A single page of [VendorApiLog] results returned by [VendorApiLogQuery].
 *
 * [page] is 0-indexed, matching the convention used by [VendorApiLogQuery.findByApiName].
 */
data class VendorApiLogPage(
    val content: List<VendorApiLog>,
    val totalElements: Long,
    val page: Int,
    val pageSize: Int,
)
