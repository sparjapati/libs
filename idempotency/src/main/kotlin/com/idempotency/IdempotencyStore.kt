package com.idempotency

/** The pluggable storage extension point. Implement against any backend (Redis, MySQL, ...). */
interface IdempotencyStore {

    /** Persists a freshly generated key in ISSUED state, unused. */
    fun issue(key: String, operation: String, ttlSeconds: Long)

    /**
     * Atomically: if the record for [key] is ISSUED and its operation matches [operation],
     * transitions it to IN_PROGRESS (storing [argsHash]) and returns [ClaimResult.Claimed].
     * Otherwise returns [ClaimResult.Existing] with the record as found (any status, any
     * operation), or [ClaimResult.NotFound] if the key was never issued or has expired.
     *
     * Implementations must make this check-and-transition atomic: two concurrent first-uses of
     * the same key must not both receive [ClaimResult.Claimed].
     */
    fun claim(key: String, operation: String, argsHash: String, ttlSeconds: Long): ClaimResult

    /** Overwrites the record for [key] as COMPLETED with the given [response]. */
    fun complete(key: String, operation: String, argsHash: String, response: String, ttlSeconds: Long)

    /** Overwrites the record for [key] as FAILED with the given exception details. */
    fun fail(
        key: String,
        operation: String,
        argsHash: String,
        exceptionClassName: String,
        exceptionMessage: String?,
        ttlSeconds: Long,
    )
}
