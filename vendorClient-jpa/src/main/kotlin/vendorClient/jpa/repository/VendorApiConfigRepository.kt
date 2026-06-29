package vendorClient.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import vendorClient.jpa.entity.VendorApiConfigEntity

interface VendorApiConfigRepository : JpaRepository<VendorApiConfigEntity, Long> {
    fun findByApiName(apiName: String): VendorApiConfigEntity?
    fun existsByApiName(apiName: String): Boolean
}
