package com.sparjapati.vendorClient.config

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VendorApiConfigTest {

    private fun config(tempDisabledUntil: Instant?) = VendorApiConfig(
        maxRequests = 10,
        windowSeconds = 60,
        enabled = true,
        tempDisabledUntil = tempDisabledUntil,
    )

    @Test fun `isTemporarilyDisabled returns false when tempDisabledUntil is null`() {
        assertFalse(config(null).isTemporarilyDisabled(Instant.now()))
    }

    @Test fun `isTemporarilyDisabled returns true when now is before tempDisabledUntil`() {
        val future = Instant.now().plusSeconds(60)
        assertTrue(config(future).isTemporarilyDisabled(Instant.now()))
    }

    @Test fun `isTemporarilyDisabled returns false when tempDisabledUntil is in the past`() {
        val past = Instant.now().minusSeconds(1)
        assertFalse(config(past).isTemporarilyDisabled(Instant.now()))
    }
}
