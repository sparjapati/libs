package vendorClient.config

import vendorClient.VendorApiKey

/**
 * Write interface for managing per-API configuration entries.
 *
 * Separating reads ([VendorApiConfigProvider]) from writes here allows callers to expose
 * only the read surface to interceptors while restricting mutation to admin/service layers.
 */
interface VendorApiConfigManager {
    /** Creates a new config entry. Throws [IllegalArgumentException] if one already exists. */
    fun createConfig(config: VendorApiConfig)

    /** Updates an existing config entry. Throws [IllegalArgumentException] if none exists. */
    fun updateConfig(config: VendorApiConfig)

    /**
     * Sets a temporary disable window ending at [until] (epoch millis).
     * Passed as `configManager::tempDisable` to the builder's rate-limiter `onTempDisable` callback.
     */
    fun tempDisable(api: VendorApiKey, until: Long)

    /** Lists all stored config entries. */
    fun listConfigs(): List<VendorApiConfig>
}
