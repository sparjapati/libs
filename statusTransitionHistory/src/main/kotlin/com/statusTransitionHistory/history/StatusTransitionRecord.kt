package com.statusTransitionHistory.history

/**
 * A single recorded status transition for a domain entity, persisted and queried via
 * [StatusTransitionStore].
 *
 * @param id this row's own generated identifier — distinct from [entityId]. `null` before persistence.
 * @param entity name of the entity type that transitioned, e.g. `"Order"`.
 * @param entityId the transitioning entity's own id, as a String, so this library stays generic
 *   across Long/UUID/String-keyed entities.
 * @param fromStatus status before the transition.
 * @param toStatus status after the transition.
 * @param comment human-readable note about the transition.
 * @param transitionedAt epoch millis when the transition happened.
 */
data class StatusTransitionRecord(
    val id: Long?,
    val entity: String,
    val entityId: String,
    val fromStatus: String,
    val toStatus: String,
    val comment: String,
    val transitionedAt: Long,
) {
    companion object {
        /**
         * Builds a not-yet-persisted [StatusTransitionRecord] for [entity]/[entityId] moving from
         * [fromStatus] to [toStatus]. [comment] defaults to `"status updated from $fromStatus to
         * $toStatus"` when not supplied.
         */
        fun forTransition(
            entity: String,
            entityId: String,
            fromStatus: String,
            toStatus: String,
            comment: String? = null,
            transitionedAt: Long = System.currentTimeMillis(),
        ): StatusTransitionRecord = StatusTransitionRecord(
            id = null,
            entity = entity,
            entityId = entityId,
            fromStatus = fromStatus,
            toStatus = toStatus,
            comment = comment ?: "status updated from $fromStatus to $toStatus",
            transitionedAt = transitionedAt,
        )
    }
}
