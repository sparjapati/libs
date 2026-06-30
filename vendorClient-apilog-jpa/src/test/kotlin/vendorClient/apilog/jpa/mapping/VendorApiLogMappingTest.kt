package vendorClient.apilog.jpa.mapping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import vendorClient.apilog.jpa.entity.VendorApiLogEntity
import vendorClient.logging.VendorApiLog

class VendorApiLogMappingTest {

    @Test fun `entity toDto maps all fields`() {
        val entity = VendorApiLogEntity(
            id = 1L, apiName = "STRIPE", requestId = "req-1",
            httpMethod = "POST", url = "https://x.com",
            requestHeaders = """{"Content-Type":["application/json"]}""",
            success = true, durationMs = 99L,
        )
        val dto = entity.toDto()
        assertEquals("STRIPE", dto.apiName)
        assertEquals("req-1", dto.requestId)
        assertEquals("POST", dto.httpMethod)
        assertEquals(listOf("application/json"), dto.requestHeaders["Content-Type"])
        assertEquals(99L, dto.durationMs)
    }

    @Test fun `log toEntity maps all fields`() {
        val log = VendorApiLog(
            apiName = "STRIPE", requestId = "req-2", httpMethod = "GET",
            url = "https://y.com",
            requestHeaders = mapOf("Accept" to listOf("application/json")),
            success = false, errorMessage = "timeout", durationMs = 200L,
        )
        val entity = log.toEntity()
        assertEquals("STRIPE", entity.apiName)
        assertEquals("timeout", entity.errorMessage)
        assertEquals("""{"Accept":["application/json"]}""", entity.requestHeaders)
    }
}
