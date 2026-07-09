package vendorClient.apilog.jpa.logging

import org.springframework.transaction.annotation.Transactional
import vendorClient.apilog.jpa.mapping.toEntity
import vendorClient.apilog.jpa.repository.VendorApiLogRepository
import vendorClient.logging.VendorApiLog
import vendorClient.logging.VendorApiLogSink

open class JpaVendorApiLogSink(
    private val repository: VendorApiLogRepository,
) : VendorApiLogSink {

    @Transactional
    override fun save(log: VendorApiLog) {
        repository.save(log.toEntity())
    }
}
