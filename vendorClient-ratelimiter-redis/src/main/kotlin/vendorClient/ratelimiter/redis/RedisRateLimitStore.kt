package vendorClient.ratelimiter.redis

import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.sync.RedisCommands
import vendorClient.VendorApiKey
import vendorClient.ratelimit.RateLimitStore

/**
 * Redis-backed sliding-window rate limiter.
 *
 * Uses a sorted set per API key. Lua atomicity guarantees that check-and-increment
 * is race-free across JVM instances — unlike [vendorClient.ratelimit.InMemoryRateLimitStore],
 * this implementation is safe for multi-node deployments.
 *
 * @param redisCommands Lettuce synchronous commands bound to a single Redis connection.
 * @param scriptSha SHA1 of [LUA_SLIDING_WINDOW_SCRIPT] pre-loaded via SCRIPT LOAD.
 * @param keyPrefix Prefix for all Redis keys. Defaults to "vendorApiRate".
 */
class RedisRateLimitStore(
    private val redisCommands: RedisCommands<String, String>,
    private val scriptSha: String,
    private val keyPrefix: String = "vendorApiRate",
) : RateLimitStore {

    override fun tryAcquire(api: VendorApiKey, maxRequests: Int, windowSeconds: Int): Boolean {
        val key = "$keyPrefix:${api.name}"
        val nowMs = System.currentTimeMillis()
        val windowMs = windowSeconds * 1_000L
        val result = redisCommands.evalsha<Long>(
            scriptSha,
            ScriptOutputType.INTEGER,
            arrayOf(key),
            nowMs.toString(),
            windowMs.toString(),
            maxRequests.toString(),
        )
        return result == 1L
    }

    companion object {
        /**
         * Lua sliding-window script. Load once via `SCRIPT LOAD` and pass the returned SHA
         * as [scriptSha] to [RedisRateLimitStore].
         *
         * KEYS[1] = rate-limit key
         * ARGV[1] = current time in milliseconds
         * ARGV[2] = window size in milliseconds
         * ARGV[3] = max requests allowed in the window
         *
         * Returns 1 (allowed) or 0 (denied).
         */
        const val LUA_SLIDING_WINDOW_SCRIPT = """
local key      = KEYS[1]
local now_ms   = tonumber(ARGV[1])
local win_ms   = tonumber(ARGV[2])
local max_req  = tonumber(ARGV[3])
local cutoff   = now_ms - win_ms
redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)
local count = redis.call('ZCARD', key)
if count < max_req then
    redis.call('ZADD', key, now_ms, now_ms .. '-' .. (count + 1))
    redis.call('PEXPIRE', key, win_ms + 1000)
    return 1
end
return 0
"""
    }
}
