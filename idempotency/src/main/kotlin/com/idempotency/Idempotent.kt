package com.idempotency

/**
 * Marks a method as idempotent for the named [operation]. The caller must supply, on the
 * configured header, a key previously obtained from [IdempotencyKeyIssuer.issue] for this same
 * [operation] — see the `idempotency` module README for the full issue-then-use flow.
 *
 * @param operation Required, explicit operation name — never derived from the method signature,
 *   so a later method rename doesn't invalidate already-issued keys.
 * @param ttlSeconds Retention after first use. -1 (default) falls back to
 *   [IdempotencyProperties.defaultTtlSeconds].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotent(
    val operation: String,
    val ttlSeconds: Long = -1L,
)
