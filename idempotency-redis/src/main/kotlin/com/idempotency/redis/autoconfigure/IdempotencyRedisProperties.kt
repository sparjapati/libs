package com.idempotency.redis.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "idempotency.redis")
data class IdempotencyRedisProperties(val keyPrefix: String = "idempotency")
