package vendorClient.jpa.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "vendorApiConfig",
    uniqueConstraints = [UniqueConstraint(name = "uq_vendor_api_config_api_name", columnNames = ["apiName"])],
)
class VendorApiConfigEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "apiName", nullable = false, length = 100)
    val apiName: String = "",

    @Column(nullable = false) var maxRequests: Int = 0,
    @Column(nullable = false) var windowSeconds: Int = 0,
    @Column(nullable = false) var enabled: Boolean = false,
    @Column var tempDisabledUntil: Instant? = null,

    // Resilience — nullable; defaults applied in toDto()
    @Column(nullable = false) var cbEnabled: Boolean = false,
    @Column var cbFailureRateThreshold: Int? = null,
    @Column var cbWaitDurationSeconds: Int? = null,
    @Column var cbSlidingWindowSize: Int? = null,
    @Column(nullable = false) var retryEnabled: Boolean = false,
    @Column var retryMaxAttempts: Int? = null,
    @Column var retryInitialIntervalMs: Long? = null,
    @Column var retryMultiplier: Double? = null,
    @Column var retryMaxIntervalMs: Long? = null,
)
