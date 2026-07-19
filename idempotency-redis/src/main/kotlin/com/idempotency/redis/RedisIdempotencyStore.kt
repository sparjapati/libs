package com.idempotency.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.idempotency.ClaimResult
import com.idempotency.IdempotencyRecord
import com.idempotency.IdempotencyStatus
import com.idempotency.IdempotencyStore
import io.lettuce.core.SetArgs
import io.lettuce.core.api.sync.RedisCommands

/**
 * Redis-backed [IdempotencyStore]. The whole [IdempotencyRecord] is stored as one JSON string
 * per key at `{keyPrefix}:{key}`.
 */
class RedisIdempotencyStore(
    private val redisCommands: RedisCommands<String, String>,
    private val objectMapper: ObjectMapper,
    private val scriptSha: String,
    private val keyPrefix: String = "idempotency",
) : IdempotencyStore {

    override fun issue(key: String, operation: String, ttlSeconds: Long) {
        val record = IdempotencyRecord(status = IdempotencyStatus.ISSUED, operation = operation)
        redisCommands.set(redisKey(key), objectMapper.writeValueAsString(record), SetArgs.Builder.nx().ex(ttlSeconds))
    }

    override fun claim(key: String, operation: String, argsHash: String, ttlSeconds: Long): ClaimResult {
        throw NotImplementedError("implemented in Task 9")
    }

    override fun complete(key: String, operation: String, argsHash: String, response: String, ttlSeconds: Long) {
        val record = IdempotencyRecord(
            status = IdempotencyStatus.COMPLETED,
            operation = operation,
            argsHash = argsHash,
            response = response,
        )
        redisCommands.set(redisKey(key), objectMapper.writeValueAsString(record), SetArgs.Builder.ex(ttlSeconds))
    }

    override fun fail(
        key: String,
        operation: String,
        argsHash: String,
        exceptionClassName: String,
        exceptionMessage: String?,
        ttlSeconds: Long,
    ) {
        val record = IdempotencyRecord(
            status = IdempotencyStatus.FAILED,
            operation = operation,
            argsHash = argsHash,
            exceptionClassName = exceptionClassName,
            exceptionMessage = exceptionMessage,
        )
        redisCommands.set(redisKey(key), objectMapper.writeValueAsString(record), SetArgs.Builder.ex(ttlSeconds))
    }

    private fun redisKey(key: String) = "$keyPrefix:$key"
}
