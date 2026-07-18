package com.statusTransitionHistory.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StatusTransitionRecordTest {

    @Test
    fun `forTransition defaults comment to a generated message`() {
        val record = StatusTransitionRecord.forTransition(
            entity = "Order",
            entityId = "123",
            fromStatus = "PENDING",
            toStatus = "PAID",
        )

        assertEquals("status updated from PENDING to PAID", record.comment)
    }

    @Test
    fun `forTransition preserves an explicit comment`() {
        val record = StatusTransitionRecord.forTransition(
            entity = "Order",
            entityId = "123",
            fromStatus = "PENDING",
            toStatus = "PAID",
            comment = "manually verified by ops",
        )

        assertEquals("manually verified by ops", record.comment)
    }

    @Test
    fun `forTransition produces an unpersisted record with a null id`() {
        val record = StatusTransitionRecord.forTransition(
            entity = "Order",
            entityId = "123",
            fromStatus = "PENDING",
            toStatus = "PAID",
        )

        assertNull(record.id)
    }
}
