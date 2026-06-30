package vendorClient.apiconfig.jpa.config

import org.springframework.transaction.annotation.Transactional
import vendorClient.VendorApiKey
import vendorClient.apiconfig.jpa.mapping.toEntity
import vendorClient.apiconfig.jpa.repository.VendorApiConfigRepository
import vendorClient.config.VendorApiConfig
import vendorClient.config.VendorApiConfigManager
import java.time.Instant

/**
 * JPA-backed [VendorApiConfigManager] that persists vendor API configuration to the database.
 *
 * All write operations are guarded with [require]/[requireNotNull] preconditions so that
 * callers receive a clear [IllegalArgumentException] with diagnostic context on misuse,
 * rather than a cryptic constraint violation from the database layer.
 */
class JpaVendorApiConfigManager(
    private val repository: VendorApiConfigRepository,
) : VendorApiConfigManager {

    @Transactional
    override fun createConfig(api: VendorApiKey, config: VendorApiConfig) {
        require(!repository.existsByApiName(api.name)) {
            "JpaVendorApiConfigManager.createConfig: config already exists for API '${api.name}'"
        }
        repository.save(config.toEntity(api.name))
    }

    @Transactional
    override fun updateConfig(api: VendorApiKey, config: VendorApiConfig) {
        val existing = requireNotNull(repository.findByApiName(api.name)) {
            "JpaVendorApiConfigManager.updateConfig: no config found for API '${api.name}'"
        }
        existing.apply {
            maxRequests = config.maxRequests
            windowSeconds = config.windowSeconds
            enabled = config.enabled
            tempDisabledUntil = config.tempDisabledUntil
            cbEnabled = config.resilience.cbEnabled
            cbFailureRateThreshold = config.resilience.cbFailureRateThreshold
            cbWaitDurationSeconds = config.resilience.cbWaitDurationSeconds
            cbSlidingWindowSize = config.resilience.cbSlidingWindowSize
            retryEnabled = config.resilience.retryEnabled
            retryMaxAttempts = config.resilience.retryMaxAttempts
            retryInitialIntervalMs = config.resilience.retryInitialIntervalMs
            retryMultiplier = config.resilience.retryMultiplier
            retryMaxIntervalMs = config.resilience.retryMaxIntervalMs
        }
        repository.save(existing)
    }

    @Transactional
    override fun tempDisable(api: VendorApiKey, until: Instant) {
        val existing = requireNotNull(repository.findByApiName(api.name)) {
            "JpaVendorApiConfigManager.tempDisable: no config found for API '${api.name}'"
        }
        existing.tempDisabledUntil = until
        repository.save(existing)
    }
}
