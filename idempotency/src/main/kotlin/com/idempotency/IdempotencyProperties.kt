package com.idempotency

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "idempotency")
data class IdempotencyProperties(
    val headerName: String = "Idempotency-Key",
    val issueTtlSeconds: Long = 900,
    val defaultTtlSeconds: Long = 86_400,
)
