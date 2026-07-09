package vendorClient.apiconfig.jpa.config

import org.springframework.transaction.annotation.Transactional
import vendorClient.VendorApiKey
import vendorClient.apiconfig.jpa.mapping.toDto
import vendorClient.apiconfig.jpa.mapping.toEntity
import vendorClient.apiconfig.jpa.repository.VendorApiConfigRepository
import vendorClient.config.VendorApiConfig
import vendorClient.config.VendorApiConfigManager

/**
 * JPA-backed [VendorApiConfigManager] that persists vendor API configuration to the database.
 *
 * All write operations are guarded with [require]/[requireNotNull] preconditions so that
 * callers receive a clear [IllegalArgumentException] with diagnostic context on misuse,
 * rather than a cryptic constraint violation from the database layer.
 */
open class JpaVendorApiConfigManager(
    private val repository: VendorApiConfigRepository,
) : VendorApiConfigManager {

    @Transactional
    override fun createConfig(config: VendorApiConfig) {
        require(!repository.existsByApiName(config.apiName)) {
            "JpaVendorApiConfigManager.createConfig: config already exists for API '${config.apiName}'"
        }
        repository.save(config.toEntity())
    }

    @Transactional
    override fun updateConfig(config: VendorApiConfig) {
        val existing = requireNotNull(repository.findByApiName(config.apiName)) {
            "JpaVendorApiConfigManager.updateConfig: no config found for API '${config.apiName}'"
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
    override fun tempDisable(api: VendorApiKey, until: Long) {
        val existing = requireNotNull(repository.findByApiName(api.name)) {
            "JpaVendorApiConfigManager.tempDisable: no config found for API '${api.name}'"
        }
        existing.tempDisabledUntil = until
        repository.save(existing)
    }

    @Transactional(readOnly = true)
    override fun listConfigs(): List<VendorApiConfig> =
        repository.findAll().map { it.toDto() }
}
