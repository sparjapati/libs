package com.idempotency.redis

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.idempotency.IdempotencyRecord
import com.idempotency.IdempotencyStatus
import io.lettuce.core.SetArgs
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RedisIdempotencyStoreTest {

    private val redisCommands: RedisCommands<String, String> = mockk(relaxed = true)
    private val objectMapper = jacksonObjectMapper()
    private val store = RedisIdempotencyStore(
        redisCommands = redisCommands,
        objectMapper = objectMapper,
        scriptSha = "sha123",
        keyPrefix = "testIdem",
    )

    @Test fun `issue writes an ISSUED record with NX and the issue TTL`() {
        val valueSlot = slot<String>()
        every { redisCommands.set("testIdem:key-1", capture(valueSlot), any<SetArgs>()) } returns "OK"

        store.issue(key = "key-1", operation = "createOrder", ttlSeconds = 900)

        val record = objectMapper.readValue(valueSlot.captured, IdempotencyRecord::class.java)
        assertEquals(IdempotencyStatus.ISSUED, record.status)
        assertEquals("createOrder", record.operation)
    }

    @Test fun `complete writes a COMPLETED record with the response and ttl`() {
        val valueSlot = slot<String>()
        every { redisCommands.set("testIdem:key-6", capture(valueSlot), any<SetArgs>()) } returns "OK"

        store.complete(key = "key-6", operation = "createOrder", argsHash = "hash-1", response = "{\"id\":1}", ttlSeconds = 86_400)

        val record = objectMapper.readValue(valueSlot.captured, IdempotencyRecord::class.java)
        assertEquals(IdempotencyStatus.COMPLETED, record.status)
        assertEquals("hash-1", record.argsHash)
        assertEquals("{\"id\":1}", record.response)
    }

    @Test fun `fail writes a FAILED record with exception details`() {
        val valueSlot = slot<String>()
        every { redisCommands.set("testIdem:key-7", capture(valueSlot), any<SetArgs>()) } returns "OK"

        store.fail(
            key = "key-7", operation = "createOrder", argsHash = "hash-1",
            exceptionClassName = "java.lang.IllegalStateException", exceptionMessage = "boom", ttlSeconds = 86_400,
        )

        val record = objectMapper.readValue(valueSlot.captured, IdempotencyRecord::class.java)
        assertEquals(IdempotencyStatus.FAILED, record.status)
        assertEquals("java.lang.IllegalStateException", record.exceptionClassName)
        assertEquals("boom", record.exceptionMessage)
    }
}
