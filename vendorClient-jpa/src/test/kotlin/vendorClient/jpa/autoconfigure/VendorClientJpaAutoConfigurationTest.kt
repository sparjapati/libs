package vendorClient.jpa.autoconfigure

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import vendorClient.jpa.repository.VendorApiConfigRepository
import vendorClient.jpa.repository.VendorApiLogRepository

class VendorClientJpaAutoConfigurationTest {

    private val configRepo: VendorApiConfigRepository = mockk()
    private val logRepo: VendorApiLogRepository = mockk()
    private val config = VendorClientJpaAutoConfiguration()

    @Test fun `jpaVendorApiConfigProvider bean is created`() =
        assertNotNull(config.jpaVendorApiConfigProvider(configRepo))

    @Test fun `jpaVendorApiConfigManager bean is created`() =
        assertNotNull(config.jpaVendorApiConfigManager(configRepo))

    @Test fun `jpaVendorApiLogSink bean is created`() =
        assertNotNull(config.jpaVendorApiLogSink(logRepo))

    @Test fun `jpaVendorApiLogQuery bean is created`() =
        assertNotNull(config.jpaVendorApiLogQuery(logRepo))
}
