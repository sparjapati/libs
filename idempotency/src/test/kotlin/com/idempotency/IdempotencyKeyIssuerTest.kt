package com.idempotency

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class IdempotencyKeyIssuerTest {

    private val store: IdempotencyStore = mockk(relaxed = true)

    @Test fun `issue generates a key and persists it via the store with the default TTL`() {
        val issuer = IdempotencyKeyIssuer(store, IdempotencyProperties(issueTtlSeconds = 900))

        val key = issuer.issue(operation = "createOrder")

        assertNotNull(key)
        verify { store.issue(key = key, operation = "createOrder", ttlSeconds = 900) }
    }

    @Test fun `issue honors an explicit ttlSeconds override`() {
        val issuer = IdempotencyKeyIssuer(store, IdempotencyProperties(issueTtlSeconds = 900))

        val key = issuer.issue(operation = "cancelOrder", ttlSeconds = 60)

        verify { store.issue(key = key, operation = "cancelOrder", ttlSeconds = 60) }
    }

    @Test fun `issue generates a distinct key on each call`() {
        val issuer = IdempotencyKeyIssuer(store, IdempotencyProperties())

        val first = issuer.issue(operation = "createOrder")
        val second = issuer.issue(operation = "createOrder")

        assertNotEquals(first, second)
    }
}
