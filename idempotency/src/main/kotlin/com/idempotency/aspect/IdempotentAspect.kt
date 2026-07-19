package com.idempotency.aspect

import com.idempotency.ClaimResult
import com.idempotency.Idempotent
import com.idempotency.IdempotencyProperties
import com.idempotency.IdempotencyRecord
import com.idempotency.IdempotencyStatus
import com.idempotency.IdempotencyStore
import com.idempotency.exception.IdempotencyInProgressException
import com.idempotency.exception.IdempotencyKeyReusedException
import com.idempotency.exception.IdempotencyOperationMismatchException
import com.idempotency.exception.IdempotentOperationFailedException
import com.idempotency.exception.MissingIdempotencyKeyException
import com.idempotency.exception.UnknownIdempotencyKeyException
import com.idempotency.support.IdempotencySupport
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** Reads [headerName] off the current request, via Spring's [RequestContextHolder]. */
fun resolveHeaderFromServletRequest(headerName: String): String? {
    val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes ?: return null
    return attributes.request.getHeader(headerName)
}

/**
 * Spring AOP [MethodInterceptor] that implements [Idempotent]-annotated methods: on first use it
 * runs the method and records the outcome (success or failure) against the caller-supplied key; on
 * a duplicate call it replays the recorded outcome instead of re-invoking the method.
 *
 * [resolveHeader] defaults to reading the configured header off the current servlet request, but
 * is overridable (e.g. in tests) since it is a plain function parameter rather than a hard
 * dependency on `RequestContextHolder`.
 */
class IdempotentAspect(
    private val store: IdempotencyStore,
    private val props: IdempotencyProperties,
    private val support: IdempotencySupport,
    private val resolveHeader: (String) -> String? = ::resolveHeaderFromServletRequest,
) : MethodInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val annotationCache = ConcurrentHashMap<Method, Optional<Idempotent>>()

    @Suppress("UNCHECKED_CAST")
    override fun invoke(invocation: MethodInvocation): Any? {
        val method = invocation.method
        val idempotent = annotationCache.getOrPut(method) {
            Optional.ofNullable(AnnotationUtils.findAnnotation(method, Idempotent::class.java))
        }.orElse(null) ?: return invocation.proceed()

        val key = resolveHeader(props.headerName)
        if (key.isNullOrBlank()) {
            throw MissingIdempotencyKeyException(
                "Missing required header '${props.headerName}' for @Idempotent operation='${idempotent.operation}'",
            )
        }

        val args = invocation.arguments as Array<Any?>
        val argsHash = support.hashArgs(args)
        val ttlSeconds = idempotent.ttlSeconds.takeIf { it > 0 } ?: props.defaultTtlSeconds

        return when (val claimResult = store.claim(key = key, operation = idempotent.operation, argsHash = argsHash, ttlSeconds = ttlSeconds)) {
            is ClaimResult.Claimed -> runAndRecord(invocation, idempotent, key, argsHash, ttlSeconds)
            ClaimResult.NotFound -> throw UnknownIdempotencyKeyException(
                "Idempotency key was never issued (or has expired) key=$key operation=${idempotent.operation}",
            )
            is ClaimResult.Existing -> handleExisting(claimResult.record, idempotent, key, argsHash, method)
        }
    }

    private fun runAndRecord(
        invocation: MethodInvocation,
        idempotent: Idempotent,
        key: String,
        argsHash: String,
        ttlSeconds: Long,
    ): Any? {
        try {
            val value = invocation.proceed()
            store.complete(key = key, operation = idempotent.operation, argsHash = argsHash, response = support.serialize(value), ttlSeconds = ttlSeconds)
            return value
        } catch (ex: Throwable) {
            log.debug("@Idempotent key={} operation={} failed, recording FAILED", key, idempotent.operation)
            store.fail(key = key, operation = idempotent.operation, argsHash = argsHash, exceptionClassName = ex.javaClass.name, exceptionMessage = ex.message, ttlSeconds = ttlSeconds)
            throw ex
        }
    }

    private fun handleExisting(
        record: IdempotencyRecord,
        idempotent: Idempotent,
        key: String,
        argsHash: String,
        method: Method,
    ): Any? {
        if (record.operation != idempotent.operation) {
            throw IdempotencyOperationMismatchException(
                "Idempotency key=$key was issued for operation='${record.operation}', not '${idempotent.operation}'",
            )
        }
        if (record.argsHash != argsHash) {
            throw IdempotencyKeyReusedException(
                "Idempotency key=$key operation='${idempotent.operation}' was reused with different arguments",
            )
        }
        return when (record.status) {
            IdempotencyStatus.ISSUED, IdempotencyStatus.IN_PROGRESS -> throw IdempotencyInProgressException(
                "Idempotency key=$key operation='${idempotent.operation}' is still in progress",
            )
            IdempotencyStatus.COMPLETED -> support.deserialize(method, requireNotNull(record.response))
            IdempotencyStatus.FAILED -> replayFailure(record)
        }
    }

    private fun replayFailure(record: IdempotencyRecord): Nothing {
        val className = requireNotNull(record.exceptionClassName)
        val reconstructed = try {
            Class.forName(className).getConstructor(String::class.java)
                .newInstance(record.exceptionMessage) as? Throwable
        } catch (ex: ReflectiveOperationException) {
            null
        }
        throw reconstructed ?: IdempotentOperationFailedException(record.exceptionMessage, className)
    }
}
