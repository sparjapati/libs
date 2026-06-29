package com.sparjapati.vendorClient

/**
 * Marker interface for client-defined vendor API identifiers.
 * Implement as an enum: `enum class MyApi : VendorApiKey { STRIPE_CHARGE, ... }`
 */
interface VendorApiKey {
    /** Unique identifier — used as DB key, Redis key, and CB instance key. */
    val name: String

    /**
     * Header name used to forward the trace/request-id on outbound calls to this API.
     * Returns null to fall back to [com.sparjapati.vendorClient.config.VendorClientSettings.requestIdHeader].
     */
    val traceHeader: String? get() = null
}
