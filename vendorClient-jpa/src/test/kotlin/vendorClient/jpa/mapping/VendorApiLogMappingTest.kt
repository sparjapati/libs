package vendorClient.jpa.mapping

import com.sparjapati.vendorClient.logging.VendorApiLog
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class VendorApiLogMappingTest {

    private fun log() = VendorApiLog(
        apiName = "STRIPE",
        requestId = "req-123",
        httpMethod = "POST",
        url = "https://api.stripe.com/v1/charges",
        requestHeaders = mapOf("Content-Type" to listOf("application/json"), "Authorization" to listOf("Bearer sk_test")),
        requestBody = """{"amount":1000}""",
        responseCode = 200,
        responseHeaders = mapOf("Content-Type" to listOf("application/json")),
        responseBody = """{"id":"ch_1"}""",
        success = true,
        errorMessage = null,
        durationMs = 123L,
    )

    @Test fun `toEntity preserves all fields`() {
        val entity = log().toEntity()
        assertEquals("STRIPE", entity.apiName)
        assertEquals("req-123", entity.requestId)
        assertEquals(200, entity.responseCode)
        assertEquals(true, entity.success)
        assertEquals(123L, entity.durationMs)
    }

    @Test fun `round-trip preserves headers`() {
        val original = log()
        val roundTrip = original.toEntity().toDto()
        assertEquals(original.requestHeaders, roundTrip.requestHeaders)
        assertEquals(original.responseHeaders, roundTrip.responseHeaders)
    }

    @Test fun `round-trip preserves null fields`() {
        val sparse = log().copy(requestBody = null, responseBody = null, errorMessage = null)
        val roundTrip = sparse.toEntity().toDto()
        assertNull(roundTrip.requestBody)
        assertNull(roundTrip.responseBody)
    }
}
