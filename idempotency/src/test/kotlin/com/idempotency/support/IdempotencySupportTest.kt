package com.idempotency.support

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

private data class Order(val id: Long, val amount: Int)

private class SampleService {
    fun createOrder(amount: Int): Order = Order(id = 1, amount = amount)
}

class IdempotencySupportTest {

    private val support = IdempotencySupport(jacksonObjectMapper())

    @Test fun `hashArgs is deterministic for the same arguments`() {
        val first = support.hashArgs(arrayOf(10, "abc"))
        val second = support.hashArgs(arrayOf(10, "abc"))
        assertEquals(first, second)
    }

    @Test fun `hashArgs differs when arguments differ`() {
        val first = support.hashArgs(arrayOf(10))
        val second = support.hashArgs(arrayOf(11))
        assertNotEquals(first, second)
    }

    @Test fun `serialize then deserialize round-trips the return value via the method's return type`() {
        val method = SampleService::class.java.getMethod("createOrder", Int::class.java)
        val order = Order(id = 1, amount = 99)

        val json = support.serialize(order)
        val restored = support.deserialize(method, json)

        assertEquals(order, restored)
    }
}
