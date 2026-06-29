package vendorClient.jpa.mapping

import com.sparjapati.vendorClient.config.VendorApiConfig
import com.sparjapati.vendorClient.config.VendorApiResilienceConfig
import vendorClient.jpa.entity.VendorApiConfigEntity
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class VendorApiConfigMappingTest {

    private fun entity(
        cbEnabled: Boolean = false,
        cbFailureRateThreshold: Int? = null,
        cbWaitDurationSeconds: Int? = null,
        cbSlidingWindowSize: Int? = null,
        retryEnabled: Boolean = false,
        retryMaxAttempts: Int? = null,
        retryInitialIntervalMs: Long? = null,
        retryMultiplier: Double? = null,
        retryMaxIntervalMs: Long? = null,
        tempDisabledUntil: Instant? = null,
    ) = VendorApiConfigEntity(
        apiName = "TEST_API",
        maxRequests = 10,
        windowSeconds = 60,
        enabled = true,
        tempDisabledUntil = tempDisabledUntil,
        cbEnabled = cbEnabled,
        cbFailureRateThreshold = cbFailureRateThreshold,
        cbWaitDurationSeconds = cbWaitDurationSeconds,
        cbSlidingWindowSize = cbSlidingWindowSize,
        retryEnabled = retryEnabled,
        retryMaxAttempts = retryMaxAttempts,
        retryInitialIntervalMs = retryInitialIntervalMs,
        retryMultiplier = retryMultiplier,
        retryMaxIntervalMs = retryMaxIntervalMs,
    )

    @Test fun `null resilience columns map to defaults`() {
        val dto = entity().toDto().resilience
        assertEquals(50, dto.cbFailureRateThreshold)
        assertEquals(30, dto.cbWaitDurationSeconds)
        assertEquals(10, dto.cbSlidingWindowSize)
        assertEquals(3, dto.retryMaxAttempts)
        assertEquals(500L, dto.retryInitialIntervalMs)
        assertEquals(2.0, dto.retryMultiplier)
        assertEquals(10_000L, dto.retryMaxIntervalMs)
    }

    @Test fun `non-null resilience columns override defaults`() {
        val dto = entity(
            cbEnabled = true, cbFailureRateThreshold = 75, cbWaitDurationSeconds = 60,
            cbSlidingWindowSize = 20, retryEnabled = true, retryMaxAttempts = 5,
            retryInitialIntervalMs = 1000L, retryMultiplier = 3.0, retryMaxIntervalMs = 30_000L,
        ).toDto().resilience
        assertEquals(75, dto.cbFailureRateThreshold)
        assertEquals(5, dto.retryMaxAttempts)
        assertEquals(3.0, dto.retryMultiplier)
    }

    @Test fun `tempDisabledUntil is preserved`() {
        val future = Instant.now().plusSeconds(300)
        assertEquals(future, entity(tempDisabledUntil = future).toDto().tempDisabledUntil)
    }

    @Test fun `round-trip toEntity then toDto preserves all fields`() {
        val config = VendorApiConfig(
            maxRequests = 5, windowSeconds = 30, enabled = true, tempDisabledUntil = null,
            resilience = VendorApiResilienceConfig(cbEnabled = true, retryEnabled = true, retryMaxAttempts = 4),
        )
        val roundTrip = config.toEntity("MY_API").toDto()
        assertEquals(config.maxRequests, roundTrip.maxRequests)
        assertEquals(config.resilience.retryMaxAttempts, roundTrip.resilience.retryMaxAttempts)
    }
}
