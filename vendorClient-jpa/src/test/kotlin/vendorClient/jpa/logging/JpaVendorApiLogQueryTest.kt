package vendorClient.jpa.logging

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import vendorClient.jpa.entity.VendorApiLogEntity
import vendorClient.jpa.repository.VendorApiLogRepository

class JpaVendorApiLogQueryTest {

    private val repository: VendorApiLogRepository = mockk()
    private val query = JpaVendorApiLogQuery(repository)

    private fun entity(requestId: String = "req-123") = VendorApiLogEntity(
        apiName = "STRIPE",
        requestId = requestId,
        httpMethod = "POST",
        url = "https://api.stripe.com",
        success = true,
        durationMs = 50L,
    )

    @Test fun `findByRequestIdPrefix returns mapped DTOs`() {
        every { repository.findByRequestIdStartingWith("req-123") } returns listOf(entity(), entity("req-123-abc"))
        val result = query.findByRequestIdPrefix("req-123")
        assertEquals(2, result.size)
    }

    @Test fun `findByApiName returns paginated result`() {
        val pageable = PageRequest.of(0, 10)
        every { repository.findByApiNameOrderByCreatedAtDesc("STRIPE", any()) } returns
            PageImpl(listOf(entity()), pageable, 1)
        val page = query.findByApiName(apiName = "STRIPE", page = 0, pageSize = 10)
        assertEquals(1, page.content.size)
        assertEquals(1L, page.totalElements)
        assertEquals(0, page.page)
    }

    @Test fun `findByApiName throws on negative page`() {
        assertThrows(IllegalArgumentException::class.java) {
            query.findByApiName(apiName = "STRIPE", page = -1, pageSize = 10)
        }
    }

    @Test fun `findByApiName throws on zero pageSize`() {
        assertThrows(IllegalArgumentException::class.java) {
            query.findByApiName(apiName = "STRIPE", page = 0, pageSize = 0)
        }
    }
}
