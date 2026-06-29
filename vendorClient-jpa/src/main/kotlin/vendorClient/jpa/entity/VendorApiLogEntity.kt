package vendorClient.jpa.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "vendorApiLogs",
    indexes = [
        Index(name = "idx_vendor_api_logs_api_name_created_at", columnList = "apiName, createdAt"),
        Index(name = "idx_vendor_api_logs_request_id", columnList = "requestId"),
    ],
)
class VendorApiLogEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "apiName", nullable = false, length = 100)
    val apiName: String = "",

    @Column(name = "requestId", nullable = false, length = 200)
    val requestId: String = "",

    @Column(nullable = false, length = 10)
    val httpMethod: String = "",

    @Column(nullable = false, length = 2048)
    val url: String = "",

    // Stored as JSON: Map<String, List<String>>
    @Column(columnDefinition = "TEXT")
    val requestHeaders: String = "{}",

    @Column(columnDefinition = "LONGTEXT")
    val requestBody: String? = null,

    @Column
    val responseCode: Int? = null,

    @Column(columnDefinition = "TEXT")
    val responseHeaders: String = "{}",

    @Column(columnDefinition = "LONGTEXT")
    val responseBody: String? = null,

    @Column(nullable = false)
    val success: Boolean = false,

    @Column(length = 1000)
    val errorMessage: String? = null,

    @Column(nullable = false)
    val durationMs: Long = 0L,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
