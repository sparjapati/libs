package vendorClient.apiconfig.jpa.mapping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import vendorClient.apiconfig.jpa.entity.VendorApiConfigEntity
import vendorClient.config.VendorApiConfig
import vendorClient.config.VendorApiResilienceConfig

class VendorApiConfigMappingTest {

    @Test fun `entity toDto maps all fields`() {
        val entity = VendorApiConfigEntity(
            id = 1L, apiName = "STRIPE",
            maxRequests = 10, windowSeconds = 60, enabled = true,
            cbEnabled = true, cbFailureRateThreshold = 40,
        )
        val dto = entity.toDto()
        assertEquals("STRIPE", dto.apiName)
        assertEquals(10, dto.maxRequests)
        assertEquals(60, dto.windowSeconds)
        assertEquals(true, dto.enabled)
        assertEquals(true, dto.resilience.cbEnabled)
        assertEquals(40, dto.resilience.cbFailureRateThreshold)
    }

    @Test fun `config toEntity maps all fields`() {
        val config = VendorApiConfig(
            apiName = "MY_API",
            maxRequests = 5, windowSeconds = 30, enabled = false,
            tempDisabledUntil = null,
            resilience = VendorApiResilienceConfig(retryEnabled = true, retryMaxAttempts = 2),
        )
        val entity = config.toEntity()
        assertEquals("MY_API", entity.apiName)
        assertEquals(5, entity.maxRequests)
        assertEquals(true, entity.retryEnabled)
        assertEquals(2, entity.retryMaxAttempts)
    }
}
