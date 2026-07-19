package com.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InMemoryIdempotencyStoreTest {

    private val store = InMemoryIdempotencyStore()

    @Test fun `issue creates an ISSUED record`() {
        store.issue(key = "k1", operation = "createOrder", ttlSeconds = 900)
        assertEquals(IdempotencyStatus.ISSUED, store.entries.getValue("k1").status)
    }

    @Test fun `claim on a freshly issued key with matching operation transitions to IN_PROGRESS and returns Claimed`() {
        store.issue(key = "k2", operation = "createOrder", ttlSeconds = 900)
        val result = store.claim(key = "k2", operation = "createOrder", argsHash = "hash-1", ttlSeconds = 900)
        assertEquals(ClaimResult.Claimed, result)
        assertEquals(IdempotencyStatus.IN_PROGRESS, store.entries.getValue("k2").status)
        assertEquals("hash-1", store.entries.getValue("k2").argsHash)
    }

    @Test fun `claim on an unknown key returns NotFound`() {
        val result = store.claim(key = "missing", operation = "createOrder", argsHash = "hash-1", ttlSeconds = 900)
        assertEquals(ClaimResult.NotFound, result)
    }

    @Test fun `claim on a key issued for a different operation returns Existing without transitioning`() {
        store.issue(key = "k3", operation = "cancelOrder", ttlSeconds = 900)
        val result = store.claim(key = "k3", operation = "createOrder", argsHash = "hash-1", ttlSeconds = 900)
        assertEquals(
            ClaimResult.Existing(IdempotencyRecord(status = IdempotencyStatus.ISSUED, operation = "cancelOrder")),
            result,
        )
    }

    @Test fun `claim on an already IN_PROGRESS key returns Existing with that record`() {
        store.issue(key = "k4", operation = "createOrder", ttlSeconds = 900)
        store.claim(key = "k4", operation = "createOrder", argsHash = "hash-1", ttlSeconds = 900)
        val result = store.claim(key = "k4", operation = "createOrder", argsHash = "hash-1", ttlSeconds = 900)
        assertEquals(
            ClaimResult.Existing(
                IdempotencyRecord(status = IdempotencyStatus.IN_PROGRESS, operation = "createOrder", argsHash = "hash-1"),
            ),
            result,
        )
    }

    @Test fun `complete overwrites the record as COMPLETED with the response`() {
        store.issue(key = "k5", operation = "createOrder", ttlSeconds = 900)
        store.complete(key = "k5", operation = "createOrder", argsHash = "hash-1", response = "{\"id\":1}", ttlSeconds = 900)
        val record = store.entries.getValue("k5")
        assertEquals(IdempotencyStatus.COMPLETED, record.status)
        assertEquals("{\"id\":1}", record.response)
    }

    @Test fun `fail overwrites the record as FAILED with exception details`() {
        store.issue(key = "k6", operation = "createOrder", ttlSeconds = 900)
        store.fail(
            key = "k6", operation = "createOrder", argsHash = "hash-1",
            exceptionClassName = "java.lang.IllegalStateException", exceptionMessage = "boom", ttlSeconds = 900,
        )
        val record = store.entries.getValue("k6")
        assertEquals(IdempotencyStatus.FAILED, record.status)
        assertEquals("java.lang.IllegalStateException", record.exceptionClassName)
        assertEquals("boom", record.exceptionMessage)
    }
}
