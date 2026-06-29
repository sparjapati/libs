package vendorClient.jpa.config

import com.sparjapati.vendorClient.VendorApiKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vendorClient.jpa.entity.VendorApiConfigEntity
import vendorClient.jpa.repository.VendorApiConfigRepository

class JpaVendorApiConfigProviderTest {

    enum class TestApi : VendorApiKey { MY_API }

    private val repository: VendorApiConfigRepository = mockk()
    private val provider = JpaVendorApiConfigProvider(repository)

    private fun entity() = VendorApiConfigEntity(
        apiName = "MY_API", maxRequests = 10, windowSeconds = 60, enabled = true,
    )

    @Test fun `returns null when no config exists`() {
        every { repository.findByApiName("MY_API") } returns null
        assertNull(provider.getConfig(TestApi.MY_API))
    }

    @Test fun `returns mapped config when entity exists`() {
        every { repository.findByApiName("MY_API") } returns entity()
        assertNotNull(provider.getConfig(TestApi.MY_API))
    }

    @Test fun `calls repository on every invocation (no cache)`() {
        every { repository.findByApiName("MY_API") } returns entity()
        provider.getConfig(TestApi.MY_API)
        provider.getConfig(TestApi.MY_API)
        verify(exactly = 2) { repository.findByApiName("MY_API") }
    }
}
