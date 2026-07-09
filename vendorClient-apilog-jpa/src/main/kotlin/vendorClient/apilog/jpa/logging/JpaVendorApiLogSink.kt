package vendorClient.apilog.jpa.logging

import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import vendorClient.apilog.jpa.mapping.toEntity
import vendorClient.apilog.jpa.repository.VendorApiLogRepository
import vendorClient.logging.VendorApiLog
import vendorClient.logging.VendorApiLogSink

open class JpaVendorApiLogSink(
    private val repository: VendorApiLogRepository,
) : VendorApiLogSink {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(JpaVendorApiLogSink::class.java)
    }

    @Transactional
    override fun save(log: VendorApiLog) {
        repository.save(log.toEntity())
        LOGGER.info(
            "Saved API log: api={}, requestId={}, attemptId={}, success={}, durationMs={}",
            log.apiName, log.requestId, log.attemptId, log.success, log.durationMs,
        )
    }
}
