package vendorClient.apiconfig.jpa.autoconfigure

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import vendorClient.apiconfig.jpa.repository.VendorApiConfigRepository

class VendorClientApiConfigJpaAutoConfigurationTest {

    private val repository: VendorApiConfigRepository = mockk()
    private val config = VendorClientApiConfigJpaAutoConfiguration()

    @Test fun `provider bean is created`() {
        assertNotNull(config.jpaVendorApiConfigProvider(repository))
    }

    @Test fun `manager bean is created`() {
        assertNotNull(config.jpaVendorApiConfigManager(repository))
    }
}
