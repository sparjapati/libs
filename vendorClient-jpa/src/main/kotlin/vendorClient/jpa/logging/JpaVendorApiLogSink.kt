package vendorClient.jpa.logging

import com.sparjapati.vendorClient.logging.VendorApiLog
import com.sparjapati.vendorClient.logging.VendorApiLogSink
import org.springframework.transaction.annotation.Transactional
import vendorClient.jpa.mapping.toEntity
import vendorClient.jpa.repository.VendorApiLogRepository

class JpaVendorApiLogSink(
    private val repository: VendorApiLogRepository,
) : VendorApiLogSink {

    @Transactional
    override fun save(log: VendorApiLog) {
        repository.save(log.toEntity())
    }
}
