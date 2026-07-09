package vendorClient.apiconfig.jpa.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import vendorClient.apiconfig.jpa.repository.VendorApiConfigRepository

class VendorApiConfigTempDisableCleanupSchedulerTest {

    private val repository: VendorApiConfigRepository = mockk()
    private val scheduler = VendorApiConfigTempDisableCleanupScheduler(repository)

    @Test fun `clears expired temp disables via the repository`() {
        every { repository.clearExpiredTempDisables(any()) } returns 3
        scheduler.clearExpiredTempDisables()
        verify(exactly = 1) { repository.clearExpiredTempDisables(any()) }
    }

    @Test fun `does nothing further when nothing was cleared`() {
        every { repository.clearExpiredTempDisables(any()) } returns 0
        scheduler.clearExpiredTempDisables()
        verify(exactly = 1) { repository.clearExpiredTempDisables(any()) }
    }
}
