package com.sparjapati.vendorClient.config

import com.sparjapati.vendorClient.VendorApiKey

/**
 * Read-only view into per-API configuration.
 *
 * Declared as a `fun interface` so callers can supply a lambda when testing
 * without standing up a full config store.
 */
fun interface VendorApiConfigProvider {
    fun getConfig(api: VendorApiKey): VendorApiConfig?
}
