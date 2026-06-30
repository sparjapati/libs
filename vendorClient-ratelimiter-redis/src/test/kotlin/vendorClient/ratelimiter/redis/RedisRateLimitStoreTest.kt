package vendorClient.ratelimiter.redis

import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import vendorClient.VendorApiKey

class RedisRateLimitStoreTest {

    enum class TestApi : VendorApiKey { STRIPE }

    private val redisCommands: RedisCommands<String, String> = mockk()
    private val store = RedisRateLimitStore(
        redisCommands = redisCommands,
        scriptSha = "abc123sha",
        keyPrefix = "testRate",
    )

    @Suppress("UNCHECKED_CAST")
    private fun stubEvalsha(result: Long) {
        every {
            redisCommands.evalsha<Any>(
                any<String>(), any<ScriptOutputType>(), any<Array<String>>(),
                any<String>(), any<String>(), any<String>(),
            )
        } returns result
    }

    @Test fun `returns true when evalsha returns 1`() {
        stubEvalsha(1L)
        assertTrue(store.tryAcquire(api = TestApi.STRIPE, maxRequests = 10, windowSeconds = 60))
    }

    @Test fun `returns false when evalsha returns 0`() {
        stubEvalsha(0L)
        assertFalse(store.tryAcquire(api = TestApi.STRIPE, maxRequests = 10, windowSeconds = 60))
    }

    @Test fun `key is composed from prefix and api name`() {
        stubEvalsha(1L)
        store.tryAcquire(api = TestApi.STRIPE, maxRequests = 5, windowSeconds = 30)
        verify {
            redisCommands.evalsha<Any>(
                eq("abc123sha"),
                eq(ScriptOutputType.INTEGER),
                match<Array<String>> { it.contentEquals(arrayOf("testRate:STRIPE")) },
                any<String>(), any<String>(), any<String>(),
            )
        }
    }

    @Test fun `window is passed in milliseconds`() {
        stubEvalsha(1L)
        store.tryAcquire(api = TestApi.STRIPE, maxRequests = 5, windowSeconds = 30)
        verify {
            redisCommands.evalsha<Any>(
                any<String>(), any<ScriptOutputType>(), any<Array<String>>(),
                any<String>(),
                eq("30000"),
                eq("5"),
            )
        }
    }
}
