package com.idempotency.aspect

import com.idempotency.ClaimResult
import com.idempotency.Idempotent
import com.idempotency.IdempotencyProperties
import com.idempotency.IdempotencyStatus
import com.idempotency.IdempotencyStore
import com.idempotency.InMemoryIdempotencyStore
import com.idempotency.exception.IdempotencyInProgressException
import com.idempotency.exception.IdempotencyKeyReusedException
import com.idempotency.exception.IdempotencyOperationMismatchException
import com.idempotency.exception.IdempotentOperationFailedException
import com.idempotency.exception.MissingIdempotencyKeyException
import com.idempotency.exception.UnknownIdempotencyKeyException
import com.idempotency.support.IdempotencySupport
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.aopalliance.intercept.MethodInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

class SampleException(message: String) : RuntimeException(message)
class NoStringConstructorException : RuntimeException("fixed-message")

class SampleService {
    @Idempotent(operation = "createOrder")
    fun createOrder(amount: Int): String = "order-for-$amount"
}

class IdempotentAspectTest {

    private val store = InMemoryIdempotencyStore()
    private val support = IdempotencySupport(jacksonObjectMapper())
    private val props = IdempotencyProperties()
    private val createOrderMethod: Method = SampleService::class.java.getMethod("createOrder", Int::class.java)

    private fun aspectWithHeader(key: String?) = IdempotentAspect(store, props, support) { key }

    private fun invocationFor(args: Array<Any?>, proceed: () -> Any?): MethodInvocation {
        val invocation: MethodInvocation = mockk()
        every { invocation.method } returns createOrderMethod
        every { invocation.arguments } returns args
        every { invocation.proceed() } answers { proceed() }
        return invocation
    }

    @Test fun `missing header throws MissingIdempotencyKeyException`() {
        val aspect = aspectWithHeader(null)
        assertThrows(MissingIdempotencyKeyException::class.java) {
            aspect.invoke(invocationFor(arrayOf(10)) { "unused" })
        }
    }

    @Test fun `unknown key throws UnknownIdempotencyKeyException`() {
        val aspect = aspectWithHeader("never-issued")
        assertThrows(UnknownIdempotencyKeyException::class.java) {
            aspect.invoke(invocationFor(arrayOf(10)) { "unused" })
        }
    }

    @Test fun `first use executes the method and stores COMPLETED`() {
        store.issue(key = "key-1", operation = "createOrder", ttlSeconds = 900)
        val aspect = aspectWithHeader("key-1")

        val result = aspect.invoke(invocationFor(arrayOf(10)) { "order-for-10" })

        assertEquals("order-for-10", result)
        assertEquals(IdempotencyStatus.COMPLETED, store.entries.getValue("key-1").status)
    }

    @Test fun `duplicate call with same args replays cached response without re-invoking`() {
        store.issue(key = "key-2", operation = "createOrder", ttlSeconds = 900)
        val aspect = aspectWithHeader("key-2")
        var invokeCount = 0

        aspect.invoke(invocationFor(arrayOf(20)) { invokeCount++; "order-for-20" })
        val replayed = aspect.invoke(invocationFor(arrayOf(20)) { invokeCount++; "order-for-20" })

        assertEquals("order-for-20", replayed)
        assertEquals(1, invokeCount)
    }

    @Test fun `same key different args throws IdempotencyKeyReusedException`() {
        store.issue(key = "key-3", operation = "createOrder", ttlSeconds = 900)
        val aspect = aspectWithHeader("key-3")
        aspect.invoke(invocationFor(arrayOf(30)) { "order-for-30" })

        assertThrows(IdempotencyKeyReusedException::class.java) {
            aspect.invoke(invocationFor(arrayOf(31)) { "order-for-31" })
        }
    }

    @Test fun `key issued for a different operation throws IdempotencyOperationMismatchException`() {
        store.issue(key = "key-4", operation = "cancelOrder", ttlSeconds = 900)
        val aspect = aspectWithHeader("key-4")

        assertThrows(IdempotencyOperationMismatchException::class.java) {
            aspect.invoke(invocationFor(arrayOf(40)) { "order-for-40" })
        }
    }

    @Test fun `concurrent in-progress duplicate throws IdempotencyInProgressException`() {
        store.issue(key = "key-5", operation = "createOrder", ttlSeconds = 900)
        store.claim(key = "key-5", operation = "createOrder", argsHash = support.hashArgs(arrayOf(50)), ttlSeconds = 900)
        val aspect = aspectWithHeader("key-5")

        assertThrows(IdempotencyInProgressException::class.java) {
            aspect.invoke(invocationFor(arrayOf(50)) { "order-for-50" })
        }
    }

    @Test fun `failure is recorded and rethrown identically on first use`() {
        store.issue(key = "key-6", operation = "createOrder", ttlSeconds = 900)
        val aspect = aspectWithHeader("key-6")

        val ex = assertThrows(SampleException::class.java) {
            aspect.invoke(invocationFor(arrayOf(60)) { throw SampleException("boom-60") })
        }

        assertEquals("boom-60", ex.message)
        assertEquals(IdempotencyStatus.FAILED, store.entries.getValue("key-6").status)
    }

    @Test fun `retry after a recorded failure replays the same exception type and message`() {
        store.issue(key = "key-7", operation = "createOrder", ttlSeconds = 900)
        val aspect = aspectWithHeader("key-7")
        assertThrows(SampleException::class.java) {
            aspect.invoke(invocationFor(arrayOf(70)) { throw SampleException("boom-70") })
        }

        val replayed = assertThrows(SampleException::class.java) {
            aspect.invoke(invocationFor(arrayOf(70)) { "should not run" })
        }
        assertEquals("boom-70", replayed.message)
    }

    @Test fun `a store failure recording COMPLETED after a successful call propagates the store's exception without recording FAILED`() {
        val mockStore = mockk<IdempotencyStore>()
        val storeException = RuntimeException("store down")
        every { mockStore.claim(key = "key-9", operation = "createOrder", argsHash = any(), ttlSeconds = props.defaultTtlSeconds) } returns ClaimResult.Claimed
        every { mockStore.complete(key = "key-9", operation = "createOrder", argsHash = any(), response = any(), ttlSeconds = props.defaultTtlSeconds) } throws storeException
        val aspect = IdempotentAspect(mockStore, props, support) { "key-9" }

        val thrown = assertThrows(RuntimeException::class.java) {
            aspect.invoke(invocationFor(arrayOf(90)) { "order-for-90" })
        }

        assertEquals("store down", thrown.message)
        verify(exactly = 0) {
            mockStore.fail(key = any(), operation = any(), argsHash = any(), exceptionClassName = any(), exceptionMessage = any(), ttlSeconds = any())
        }
    }

    @Test fun `retry after a failure whose type cannot be reconstructed wraps in IdempotentOperationFailedException`() {
        store.issue(key = "key-8", operation = "createOrder", ttlSeconds = 900)
        val aspect = aspectWithHeader("key-8")
        assertThrows(NoStringConstructorException::class.java) {
            aspect.invoke(invocationFor(arrayOf(80)) { throw NoStringConstructorException() })
        }

        assertThrows(IdempotentOperationFailedException::class.java) {
            aspect.invoke(invocationFor(arrayOf(80)) { "should not run" })
        }
    }
}
