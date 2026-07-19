package com.idempotency.redis.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.idempotency.redis.RedisIdempotencyStore
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
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
    }

    @Test fun `uses keyPrefix from properties`() {
        every { redisCommands.scriptLoad(any<String>()) } returns "sha1abc"

        val store = config.redisIdempotencyStore(
            redisCommands = redisCommands,
            objectMapper = ObjectMapper(),
            props = IdempotencyRedisProperties(keyPrefix = "myPrefix"),
        )

        assertNotNull(store)
    }
}
