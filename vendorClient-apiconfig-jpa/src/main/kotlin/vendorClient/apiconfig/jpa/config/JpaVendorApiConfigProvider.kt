package vendorClient.apiconfig.jpa.config

import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import vendorClient.VendorApiKey
import vendorClient.apiconfig.jpa.mapping.toDto
import vendorClient.apiconfig.jpa.repository.VendorApiConfigRepository
import vendorClient.config.VendorApiConfig
import vendorClient.config.VendorApiConfigProvider

/**
 * JPA-backed [VendorApiConfigProvider] that reads directly from the database on every call.
 *
 * Intentionally has no in-process cache — callers that need caching should wrap this
 * with a caching decorator rather than having caching baked in here.
 */
open class JpaVendorApiConfigProvider(
    private val repository: VendorApiConfigRepository,
) : VendorApiConfigProvider {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(JpaVendorApiConfigProvider::class.java)
    }

    @Transactional(readOnly = true)
    override fun getConfig(api: VendorApiKey): VendorApiConfig? =
        repository.findByApiName(api.name)?.toDto()
            ?: run {
                LOGGER.debug("No VendorApiConfig found for API '{}'", api.name)
                null
            }
}
