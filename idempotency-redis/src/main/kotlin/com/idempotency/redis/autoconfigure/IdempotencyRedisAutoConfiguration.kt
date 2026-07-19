package com.idempotency.redis.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.idempotency.IdempotencyStore
import com.idempotency.redis.RedisIdempotencyStore
import io.lettuce.core.api.sync.RedisCommands
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdempotencyRedisProperties::class)
class IdempotencyRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore::class)
    fun redisIdempotencyStore(
        redisCommands: RedisCommands<String, String>,
        objectMapper: ObjectMapper,
        props: IdempotencyRedisProperties,
    ): IdempotencyStore {
        val scriptSha = redisCommands.scriptLoad(RedisIdempotencyStore.LUA_CLAIM_SCRIPT)
        return RedisIdempotencyStore(
            redisCommands = redisCommands,
            objectMapper = objectMapper,
            scriptSha = scriptSha,
            keyPrefix = props.keyPrefix,
        )
    }
}
