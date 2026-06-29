package vendorClient.jpa.config

import com.sparjapati.vendorClient.VendorApiKey
import com.sparjapati.vendorClient.config.VendorApiConfig
import com.sparjapati.vendorClient.config.VendorApiConfigProvider
import org.springframework.transaction.annotation.Transactional
import vendorClient.jpa.mapping.toDto
import vendorClient.jpa.repository.VendorApiConfigRepository

/**
 * JPA-backed [VendorApiConfigProvider] that reads directly from the database on every call.
 *
 * Intentionally has no in-process cache — callers that need caching should wrap this
 * with a caching decorator rather than having caching baked in here.
 */
class JpaVendorApiConfigProvider(
    private val repository: VendorApiConfigRepository,
) : VendorApiConfigProvider {

    @Transactional(readOnly = true)
    override fun getConfig(api: VendorApiKey): VendorApiConfig? =
        repository.findByApiName(api.name)?.toDto()
}
