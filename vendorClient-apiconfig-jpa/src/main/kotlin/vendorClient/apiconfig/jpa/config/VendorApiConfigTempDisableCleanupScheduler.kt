package vendorClient.apiconfig.jpa.config

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import vendorClient.apiconfig.jpa.repository.VendorApiConfigRepository

/**
 * Periodically clears [vendorClient.apiconfig.jpa.entity.VendorApiConfigEntity.tempDisabledUntil]
 * once its cooldown has passed.
 *
 * This is a cleanup convenience, not a correctness requirement:
 * [vendorClient.config.VendorApiConfig.isTemporarilyDisabled] already treats a past
 * `tempDisabledUntil` as "not disabled" regardless of whether this job has run yet, so a delayed
 * cleanup never causes incorrect rate-limit enforcement. It exists so stored rows — and anything
 * reading them directly, e.g. an admin UI via [vendorClient.config.VendorApiConfigManager.listConfigs] —
 * reflect current state instead of a stale past cooldown.
 *
 * Interval configurable via `vendor-client.api-config.temp-disable-cleanup.interval-ms`
 * (default 60000). Disable entirely via `vendor-client.api-config.temp-disable-cleanup.enabled=false`.
 */
open class VendorApiConfigTempDisableCleanupScheduler(
    private val repository: VendorApiConfigRepository,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(VendorApiConfigTempDisableCleanupScheduler::class.java)
    }

    @Scheduled(fixedRateString = "\${vendor-client.api-config.temp-disable-cleanup.interval-ms:60000}")
    @Transactional
    open fun clearExpiredTempDisables() {
        val cleared = repository.clearExpiredTempDisables(System.currentTimeMillis())
        if (cleared > 0) {
            LOGGER.info("Cleared tempDisabledUntil for {} vendor API config(s) whose cooldown has expired", cleared)
        }
    }
}
