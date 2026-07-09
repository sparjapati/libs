package vendorClient.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VendorApiConfigTest {

    private fun config(tempDisabledUntil: Long?) = VendorApiConfig(
        apiName = "STRIPE",
        maxRequests = 10,
        windowSeconds = 60,
        enabled = true,
        tempDisabledUntil = tempDisabledUntil,
    )

    @Test fun `isTemporarilyDisabled returns false when tempDisabledUntil is null`() {
        assertFalse(config(null).isTemporarilyDisabled(System.currentTimeMillis()))
    }

    @Test fun `isTemporarilyDisabled returns true when now is before tempDisabledUntil`() {
        val now = System.currentTimeMillis()
        val future = now + 60_000
        assertTrue(config(future).isTemporarilyDisabled(now))
    }

    @Test fun `isTemporarilyDisabled returns false when tempDisabledUntil is in the past`() {
        val now = System.currentTimeMillis()
        val past = now - 1_000
        assertFalse(config(past).isTemporarilyDisabled(now))
    }
}
