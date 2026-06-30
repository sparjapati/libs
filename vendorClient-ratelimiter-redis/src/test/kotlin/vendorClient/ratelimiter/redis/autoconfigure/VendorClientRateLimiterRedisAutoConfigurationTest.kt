package vendorClient.ratelimiter.redis.autoconfigure

import io.mockk.every
import io.mockk.mockk
import io.lettuce.core.api.sync.RedisCommands
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import vendorClient.config.VendorClientSettings

class VendorClientRateLimiterRedisAutoConfigurationTest {

    private val redisCommands: RedisCommands<String, String> = mockk()
    private val config = VendorClientRateLimiterRedisAutoConfiguration()

    @Test fun `bean is created with settings`() {
        every { redisCommands.scriptLoad(any<String>()) } returns "sha1abc"
        assertNotNull(config.redisRateLimitStore(redisCommands = redisCommands, settings = VendorClientSettings()))
    }

    @Test fun `bean is created without settings`() {
        every { redisCommands.scriptLoad(any<String>()) } returns "sha1abc"
        assertNotNull(config.redisRateLimitStore(redisCommands = redisCommands, settings = null))
    }

    @Test fun `uses rateLimiterKeyPrefix from settings`() {
        every { redisCommands.scriptLoad(any<String>()) } returns "sha1abc"
        assertNotNull(config.redisRateLimitStore(
            redisCommands = redisCommands,
            settings = VendorClientSettings(rateLimiterKeyPrefix = "myPrefix"),
        ))
    }
}
