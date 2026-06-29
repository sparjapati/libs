package vendorClient.jpa.config

import com.sparjapati.vendorClient.VendorApiKey
import com.sparjapati.vendorClient.config.VendorApiConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import vendorClient.jpa.entity.VendorApiConfigEntity
import vendorClient.jpa.repository.VendorApiConfigRepository
import java.time.Instant

class JpaVendorApiConfigManagerTest {

    enum class TestApi : VendorApiKey { MY_API }

    private val repository: VendorApiConfigRepository = mockk()
    private val manager = JpaVendorApiConfigManager(repository)

    private fun config() = VendorApiConfig(
        maxRequests = 10, windowSeconds = 60, enabled = true, tempDisabledUntil = null,
    )

    private fun entity() = VendorApiConfigEntity(
        apiName = "MY_API", maxRequests = 10, windowSeconds = 60, enabled = true,
    )

    @Test fun `createConfig saves new entity`() {
        every { repository.existsByApiName("MY_API") } returns false
        every { repository.save(any()) } returnsArgument 0
        manager.createConfig(TestApi.MY_API, config())
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test fun `createConfig throws when config already exists`() {
        every { repository.existsByApiName("MY_API") } returns true
        assertThrows(IllegalArgumentException::class.java) {
            manager.createConfig(TestApi.MY_API, config())
        }
    }

    @Test fun `updateConfig saves updated entity`() {
        every { repository.findByApiName("MY_API") } returns entity()
        every { repository.save(any()) } returnsArgument 0
        manager.updateConfig(TestApi.MY_API, config().copy(maxRequests = 20))
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test fun `updateConfig throws when no config exists`() {
        every { repository.findByApiName("MY_API") } returns null
        assertThrows(IllegalArgumentException::class.java) {
            manager.updateConfig(TestApi.MY_API, config())
        }
    }

    @Test fun `tempDisable sets tempDisabledUntil and saves`() {
        val entity = entity()
        val until = Instant.now().plusSeconds(300)
        every { repository.findByApiName("MY_API") } returns entity
        every { repository.save(any()) } returnsArgument 0
        manager.tempDisable(TestApi.MY_API, until)
        verify(exactly = 1) { repository.save(match { it.tempDisabledUntil == until }) }
    }

    @Test fun `tempDisable throws when no config exists`() {
        every { repository.findByApiName("MY_API") } returns null
        assertThrows(IllegalArgumentException::class.java) {
            manager.tempDisable(TestApi.MY_API, Instant.now())
        }
    }
}
