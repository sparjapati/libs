package vendorClient.apiconfig.jpa.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vendorClient.VendorApiKey
import vendorClient.apiconfig.jpa.entity.VendorApiConfigEntity
import vendorClient.apiconfig.jpa.repository.VendorApiConfigRepository

class JpaVendorApiConfigProviderTest {

    enum class Api : VendorApiKey { STRIPE }

    private val repository: VendorApiConfigRepository = mockk()
    private val provider = JpaVendorApiConfigProvider(repository)

    @Test fun `returns dto when entity found`() {
        every { repository.findByApiName("STRIPE") } returns VendorApiConfigEntity(
            id = 1L, apiName = "STRIPE", maxRequests = 5, windowSeconds = 60, enabled = true,
        )
        assertNotNull(provider.getConfig(Api.STRIPE))
    }

    @Test fun `returns null when entity not found`() {
        every { repository.findByApiName("STRIPE") } returns null
        assertNull(provider.getConfig(Api.STRIPE))
    }
}
