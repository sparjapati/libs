package vendorClient.apilog.jpa.logging

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import vendorClient.apilog.jpa.entity.VendorApiLogEntity
import vendorClient.apilog.jpa.repository.VendorApiLogRepository

class JpaVendorApiLogQueryTest {

    private val repository: VendorApiLogRepository = mockk()
    private val query = JpaVendorApiLogQuery(repository)

    @Test fun `findByRequestIdPrefix returns mapped logs`() {
        every { repository.findByRequestIdStartingWith("req-") } returns listOf(
            VendorApiLogEntity(id = 1L, apiName = "STRIPE", requestId = "req-1", httpMethod = "GET",
                url = "https://x.com", success = true, durationMs = 10L)
        )
        val result = query.findByRequestIdPrefix("req-")
        assertEquals(1, result.size)
        assertEquals("STRIPE", result[0].apiName)
    }

    @Test fun `findByApiName throws on negative page`() {
        assertThrows<IllegalArgumentException> {
            query.findByApiName(apiName = "STRIPE", page = -1, pageSize = 10)
        }
    }

    @Test fun `findByApiName throws on zero pageSize`() {
        assertThrows<IllegalArgumentException> {
            query.findByApiName(apiName = "STRIPE", page = 0, pageSize = 0)
        }
    }

    @Test fun `findByApiName returns page result`() {
        val entity = VendorApiLogEntity(id = 1L, apiName = "STRIPE", requestId = "req-1",
            httpMethod = "GET", url = "https://x.com", success = true, durationMs = 5L)
        every { repository.findByApiNameOrderByCreatedAtDesc("STRIPE", any()) } returns PageImpl(listOf(entity))
        val page = query.findByApiName(apiName = "STRIPE", page = 0, pageSize = 10)
        assertEquals(1, page.content.size)
        assertEquals(1L, page.totalElements)
    }
}
