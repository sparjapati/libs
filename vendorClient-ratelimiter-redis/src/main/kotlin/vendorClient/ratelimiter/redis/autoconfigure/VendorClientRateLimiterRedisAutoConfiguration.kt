package vendorClient.ratelimiter.redis.autoconfigure

import io.lettuce.core.api.sync.RedisCommands
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import vendorClient.config.VendorClientSettings
import vendorClient.ratelimit.RateLimitStore
import vendorClient.ratelimiter.redis.RedisRateLimitStore

@Configuration(proxyBeanMethods = false)
class VendorClientRateLimiterRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RateLimitStore::class)
    fun redisRateLimitStore(
        redisCommands: RedisCommands<String, String>,
        settings: VendorClientSettings?,
    ): RedisRateLimitStore {
        val scriptSha = redisCommands.scriptLoad(RedisRateLimitStore.LUA_SLIDING_WINDOW_SCRIPT)
        return RedisRateLimitStore(
            redisCommands = redisCommands,
            scriptSha = scriptSha,
            keyPrefix = settings?.rateLimiterKeyPrefix ?: "vendorApiRate",
        )
    }
}
