package com.idempotency.redis.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.idempotency.redis.RedisIdempotencyStore
import io.lettuce.core.SetArgs
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdempotencyRedisAutoConfigurationTest {

    private val redisCommands: RedisCommands<String, String> = mockk()
    private val config = IdempotencyRedisAutoConfiguration()

    @Test fun `redisIdempotencyStore bean is created and loads the claim script`() {
        every { redisCommands.scriptLoad(any<String>()) } returns "sha1abc"

        val store = config.redisIdempotencyStore(
            redisCommands = redisCommands,
            objectMapper = ObjectMapper(),
            props = IdempotencyRedisProperties(),
        )

        assertNotNull(store)
        assertTrue(store is RedisIdempotencyStore)
        verify { redisCommands.scriptLoad(RedisIdempotencyStore.LUA_CLAIM_SCRIPT) }
    }

    @Test fun `uses keyPrefix from properties`() {
        every { redisCommands.scriptLoad(any<String>()) } returns "sha1abc"
        every { redisCommands.set(any<String>(), any<String>(), any<SetArgs>()) } returns "OK"

        val store = config.redisIdempotencyStore(
            redisCommands = redisCommands,
            objectMapper = ObjectMapper(),
            props = IdempotencyRedisProperties(keyPrefix = "myPrefix"),
        )

        assertNotNull(store)
        store.issue(key = "k1", operation = "op", ttlSeconds = 900)
        verify { redisCommands.set(eq("myPrefix:k1"), any<String>(), any<SetArgs>()) }
    }
}
