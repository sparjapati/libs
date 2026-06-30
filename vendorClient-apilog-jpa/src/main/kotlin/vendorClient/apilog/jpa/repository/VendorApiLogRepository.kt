package vendorClient.apilog.jpa.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import vendorClient.apilog.jpa.entity.VendorApiLogEntity

interface VendorApiLogRepository : JpaRepository<VendorApiLogEntity, Long> {

    @Query("SELECT l FROM VendorApiLogEntity l WHERE l.requestId LIKE :prefix% ORDER BY l.createdAt DESC")
    fun findByRequestIdStartingWith(@Param("prefix") prefix: String): List<VendorApiLogEntity>

    fun findByApiNameOrderByCreatedAtDesc(apiName: String, pageable: Pageable): Page<VendorApiLogEntity>
}
