package com.idempotency.exception

/** Thrown replaying a FAILED record when [originalExceptionClassName] couldn't be reconstructed. */
class IdempotentOperationFailedException(
    message: String?,
    val originalExceptionClassName: String,
) : RuntimeException(message)
