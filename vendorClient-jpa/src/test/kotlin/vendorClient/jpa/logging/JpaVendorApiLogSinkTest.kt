package vendorClient.jpa.logging

import vendorClient.logging.VendorApiLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import vendorClient.jpa.repository.VendorApiLogRepository

class JpaVendorApiLogSinkTest {

    private val repository: VendorApiLogRepository = mockk()
    private val sink = JpaVendorApiLogSink(repository)

    private fun log() = VendorApiLog(
        apiName = "STRIPE",
        requestId = "req-1",
        httpMethod = "POST",
        url = "https://api.stripe.com/v1/charges",
        success = true,
        durationMs = 50L,
    )

    @Test fun `save calls repository with mapped entity`() {
        every { repository.save(any()) } returnsArgument 0
        sink.save(log())
        verify(exactly = 1) { repository.save(any()) }
    }
}
