package vendorClient.apilog.jpa.autoconfigure

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import vendorClient.apilog.jpa.repository.VendorApiLogRepository

class VendorClientApiLogJpaAutoConfigurationTest {

    private val repository: VendorApiLogRepository = mockk()
    private val config = VendorClientApiLogJpaAutoConfiguration()

    @Test fun `sink bean is created`() {
        assertNotNull(config.jpaVendorApiLogSink(repository))
    }

    @Test fun `query bean is created`() {
        assertNotNull(config.jpaVendorApiLogQuery(repository))
    }
}
