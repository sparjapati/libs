package vendorClient.apiconfig.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import vendorClient.apiconfig.jpa.entity.VendorApiConfigEntity

interface VendorApiConfigRepository : JpaRepository<VendorApiConfigEntity, Long> {
    fun findByApiName(apiName: String): VendorApiConfigEntity?
    fun existsByApiName(apiName: String): Boolean

    /** Clears [VendorApiConfigEntity.tempDisabledUntil] on every row whose cooldown has passed [now]. Returns the row count updated. */
    @Modifying
    @Query("UPDATE VendorApiConfigEntity e SET e.tempDisabledUntil = null WHERE e.tempDisabledUntil IS NOT NULL AND e.tempDisabledUntil <= :now")
    fun clearExpiredTempDisables(@Param("now") now: Long): Int
}
