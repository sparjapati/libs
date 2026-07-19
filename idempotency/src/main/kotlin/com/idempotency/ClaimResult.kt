package com.idempotency

/** Outcome of [IdempotencyStore.claim]. */
sealed interface ClaimResult {
    /** This call now owns the key — proceed with the annotated method. */
    data object Claimed : ClaimResult

    /** The key exists but wasn't (re-)claimed by this call — caller must interpret [record]. */
    data class Existing(val record: IdempotencyRecord) : ClaimResult

    /** The key was never issued, or its ISSUED-state TTL has expired. */
    data object NotFound : ClaimResult
}
