package com.idempotency

import java.util.concurrent.ConcurrentHashMap

/** In-memory [IdempotencyStore] test fake, standing in for a real Redis/MySQL/user-defined backend. */
internal class InMemoryIdempotencyStore : IdempotencyStore {
    val entries = ConcurrentHashMap<String, IdempotencyRecord>()

    override fun issue(key: String, operation: String, ttlSeconds: Long) {
        entries[key] = IdempotencyRecord(status = IdempotencyStatus.ISSUED, operation = operation)
    }

    @Synchronized
    override fun claim(key: String, operation: String, argsHash: String, ttlSeconds: Long): ClaimResult {
        val existing = entries[key] ?: return ClaimResult.NotFound
        if (existing.status == IdempotencyStatus.ISSUED && existing.operation == operation) {
            entries[key] = existing.copy(status = IdempotencyStatus.IN_PROGRESS, argsHash = argsHash)
            return ClaimResult.Claimed
        }
        return ClaimResult.Existing(existing)
    }

    override fun complete(key: String, operation: String, argsHash: String, response: String, ttlSeconds: Long) {
        entries[key] = IdempotencyRecord(
            status = IdempotencyStatus.COMPLETED,
            operation = operation,
            argsHash = argsHash,
            response = response,
        )
    }

    override fun fail(
        key: String,
        operation: String,
        argsHash: String,
        exceptionClassName: String,
        exceptionMessage: String?,
        ttlSeconds: Long,
    ) {
        entries[key] = IdempotencyRecord(
            status = IdempotencyStatus.FAILED,
            operation = operation,
            argsHash = argsHash,
            exceptionClassName = exceptionClassName,
            exceptionMessage = exceptionMessage,
        )
    }
}
