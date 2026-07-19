package com.idempotency.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdempotencyExceptionsTest {

    @Test fun `MissingIdempotencyKeyException preserves its message`() {
        assertEquals("missing", MissingIdempotencyKeyException("missing").message)
    }

    @Test fun `UnknownIdempotencyKeyException preserves its message`() {
        assertEquals("unknown", UnknownIdempotencyKeyException("unknown").message)
    }

    @Test fun `IdempotencyOperationMismatchException preserves its message`() {
        assertEquals("mismatch", IdempotencyOperationMismatchException("mismatch").message)
    }

    @Test fun `IdempotencyKeyReusedException preserves its message`() {
        assertEquals("reused", IdempotencyKeyReusedException("reused").message)
    }

    @Test fun `IdempotencyInProgressException preserves its message`() {
        assertEquals("in-progress", IdempotencyInProgressException("in-progress").message)
    }

    @Test fun `IdempotentOperationFailedException preserves message and original exception class name`() {
        val ex = IdempotentOperationFailedException("boom", "com.example.SomeException")
        assertEquals("boom", ex.message)
        assertEquals("com.example.SomeException", ex.originalExceptionClassName)
    }
}
