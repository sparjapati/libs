package com.idempotency.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.idempotency.ClaimResult
import com.idempotency.IdempotencyRecord
import com.idempotency.IdempotencyStatus
import com.idempotency.IdempotencyStore
import io.lettuce.core.ScriptOutputType
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
        val result = redisCommands.evalsha<String>(
            scriptSha,
            ScriptOutputType.VALUE,
            arrayOf(redisKey(key)),
            operation, argsHash, ttlSeconds.toString(),
        )
        return when (result) {
            SENTINEL_NOT_FOUND -> ClaimResult.NotFound
            SENTINEL_CLAIMED -> ClaimResult.Claimed
            else -> ClaimResult.Existing(objectMapper.readValue(result, IdempotencyRecord::class.java))
        }
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

    companion object {
        private const val SENTINEL_NOT_FOUND = "__IDEMPOTENCY_NOT_FOUND__"
        private const val SENTINEL_CLAIMED = "__IDEMPOTENCY_CLAIMED__"

        /**
         * KEYS[1] = fully-prefixed idempotency key
         * ARGV[1] = operation, ARGV[2] = argsHash, ARGV[3] = ttlSeconds
         *
         * Atomically: if the record is ISSUED and its operation matches ARGV[1], rewrites it to
         * IN_PROGRESS with ARGV[2] and a fresh EX ttl, returning the CLAIMED sentinel. Otherwise
         * returns the record's raw JSON unchanged, or the NOT_FOUND sentinel if the key is absent.
         */
        val LUA_CLAIM_SCRIPT = """
local raw = redis.call('GET', KEYS[1])
if not raw then
    return '$SENTINEL_NOT_FOUND'
end
local record = cjson.decode(raw)
if record.status == 'ISSUED' and record.operation == ARGV[1] then
    record.status = 'IN_PROGRESS'
    record.argsHash = ARGV[2]
    redis.call('SET', KEYS[1], cjson.encode(record), 'EX', ARGV[3])
    return '$SENTINEL_CLAIMED'
end
return raw
""".trimIndent()
    }
}
