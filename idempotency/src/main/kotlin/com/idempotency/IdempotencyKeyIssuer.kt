package com.idempotency

import java.util.UUID

class IdempotencyKeyIssuer(
    private val store: IdempotencyStore,
    private val props: IdempotencyProperties,
) {
    fun issue(operation: String, ttlSeconds: Long = props.issueTtlSeconds): String {
        val key = UUID.randomUUID().toString()
        store.issue(key = key, operation = operation, ttlSeconds = ttlSeconds)
        return key
    }
}
