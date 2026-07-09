package vendorClient.apilog.jpa.logging

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import vendorClient.apilog.jpa.repository.VendorApiLogRepository
import vendorClient.logging.VendorApiLog

class JpaVendorApiLogSinkTest {

    private val repository: VendorApiLogRepository = mockk()
    private val sink = JpaVendorApiLogSink(repository)

    private fun log() = VendorApiLog(
        apiName = "STRIPE", requestId = "req-1", attemptId = "attempt-1", httpMethod = "GET",
        url = "https://api.stripe.com", success = true, durationMs = 42L,
    )

    @Test fun `save persists the log entity`() {
        every { repository.save(any()) } returnsArgument 0
        sink.save(log())
        verify { repository.save(any()) }
    }
}
