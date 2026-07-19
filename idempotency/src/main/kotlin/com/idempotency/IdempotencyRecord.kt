package com.idempotency

/**
 * A stored idempotency record. [argsHash] is null only while [status] is [IdempotencyStatus.ISSUED]
 * (not yet claimed). [response] is set only when [status] is [IdempotencyStatus.COMPLETED].
 * [exceptionClassName]/[exceptionMessage] are set only when [status] is [IdempotencyStatus.FAILED].
 */
data class IdempotencyRecord(
    val status: IdempotencyStatus,
    val operation: String,
    val argsHash: String? = null,
    val response: String? = null,
    val exceptionClassName: String? = null,
    val exceptionMessage: String? = null,
)
