package com.idempotency.redis

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.idempotency.ClaimResult
import com.idempotency.IdempotencyRecord
import com.idempotency.IdempotencyStatus
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test fun `claim returns Claimed when evalsha returns the CLAIMED sentinel`() {
        every {
            redisCommands.evalsha<String>(
                any<String>(), any<ScriptOutputType>(), any<Array<String>>(),
                any<String>(), any<String>(), any<String>(),
            )
        } returns "__IDEMPOTENCY_CLAIMED__"

        val result = store.claim(key = "key-2", operation = "createOrder", argsHash = "hash-1", ttlSeconds = 900)

        assertEquals(ClaimResult.Claimed, result)
    }

    @Test fun `claim returns NotFound when evalsha returns the NOT_FOUND sentinel`() {
        every {
            redisCommands.evalsha<String>(
                any<String>(), any<ScriptOutputType>(), any<Array<String>>(),
                any<String>(), any<String>(), any<String>(),
            )
        } returns "__IDEMPOTENCY_NOT_FOUND__"

        val result = store.claim(key = "key-3", operation = "createOrder", argsHash = "hash-1", ttlSeconds = 900)

        assertEquals(ClaimResult.NotFound, result)
    }

    @Test fun `claim returns Existing decoded from the raw JSON when the script doesn't claim`() {
        val existingJson = objectMapper.writeValueAsString(
            IdempotencyRecord(status = IdempotencyStatus.IN_PROGRESS, operation = "createOrder", argsHash = "hash-1"),
        )
        every {
            redisCommands.evalsha<String>(
                any<String>(), any<ScriptOutputType>(), any<Array<String>>(),
                any<String>(), any<String>(), any<String>(),
            )
        } returns existingJson

        val result = store.claim(key = "key-4", operation = "createOrder", argsHash = "hash-1", ttlSeconds = 900)

        assertTrue(result is ClaimResult.Existing)
        assertEquals(IdempotencyStatus.IN_PROGRESS, (result as ClaimResult.Existing).record.status)
    }

    @Test fun `claim passes the prefixed key, operation, argsHash and ttl to the script`() {
        every {
            redisCommands.evalsha<String>(
                any<String>(), any<ScriptOutputType>(), any<Array<String>>(),
                any<String>(), any<String>(), any<String>(),
            )
        } returns "__IDEMPOTENCY_CLAIMED__"

        store.claim(key = "key-5", operation = "createOrder", argsHash = "hash-9", ttlSeconds = 300)

        verify {
            redisCommands.evalsha<String>(
                eq("sha123"),
                eq(ScriptOutputType.VALUE),
                match<Array<String>> { it.contentEquals(arrayOf("testIdem:key-5")) },
                eq("createOrder"), eq("hash-9"), eq("300"),
            )
        }
    }
}
