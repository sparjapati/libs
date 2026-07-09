package vendorClient.apiconfig.jpa.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vendorClient.VendorApiKey
import vendorClient.apiconfig.jpa.entity.VendorApiConfigEntity
import vendorClient.apiconfig.jpa.repository.VendorApiConfigRepository
import vendorClient.config.VendorApiConfig
import vendorClient.config.VendorApiResilienceConfig

class JpaVendorApiConfigManagerTest {

    enum class Api : VendorApiKey { STRIPE }

    private val repository: VendorApiConfigRepository = mockk()
    private val manager = JpaVendorApiConfigManager(repository)

    private fun config() = VendorApiConfig(
        apiName = "STRIPE",
        maxRequests = 10,
        windowSeconds = 60,
        enabled = true,
        tempDisabledUntil = null,
        resilience = VendorApiResilienceConfig(),
    )

    private fun entity(apiName: String = "STRIPE") = VendorApiConfigEntity(
        id = 1L,
        apiName = apiName,
        maxRequests = 10,
        windowSeconds = 60,
        enabled = true,
    )

    @Test fun `createConfig saves new entity`() {
        every { repository.existsByApiName("STRIPE") } returns false
        every { repository.save(any()) } returnsArgument 0
        manager.createConfig(config = config())
        verify { repository.save(any()) }
    }

    @Test fun `createConfig throws when config already exists`() {
        every { repository.existsByApiName("STRIPE") } returns true
        assertThrows<IllegalArgumentException> {
            manager.createConfig(config = config())
        }
    }

    @Test fun `updateConfig throws when config not found`() {
        every { repository.findByApiName("STRIPE") } returns null
        assertThrows<IllegalArgumentException> {
            manager.updateConfig(config = config())
        }
    }

    @Test fun `tempDisable updates entity`() {
        val e = entity()
        every { repository.findByApiName("STRIPE") } returns e
        every { repository.save(any()) } returnsArgument 0
        val until = System.currentTimeMillis() + 300_000
        manager.tempDisable(api = Api.STRIPE, until = until)
        verify { repository.save(match { it.tempDisabledUntil == until }) }
    }

    @Test fun `listConfigs maps every stored entity`() {
        every { repository.findAll() } returns listOf(entity("STRIPE"), entity("PAYPAL"))
        val entries = manager.listConfigs()
        assertEquals(listOf("STRIPE", "PAYPAL"), entries.map { it.apiName })
    }
}
