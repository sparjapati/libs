package com.statusTransitionHistory.history

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Persists and queries append-only [StatusTransitionRecord]s.
 *
 * There is no no-op/in-memory default implementation — this is an audit log, and silently
 * discarding a [record] call would hide data loss. Add a store adapter module (e.g.
 * `statusTransitionHistory-mysql`) to the classpath so a [StatusTransitionStore] bean is
 * available; otherwise injection fails at startup.
 */
interface StatusTransitionStore {

    /**
     * Records a new transition as an INSERT — every call produces a new row, never an update.
     * [comment] defaults to `"status updated from $fromStatus to $toStatus"` when omitted.
     * Returns the persisted record, including its generated [StatusTransitionRecord.id].
     */
    fun record(
        entity: String,
        entityId: String,
        fromStatus: String,
        toStatus: String,
        comment: String? = null,
    ): StatusTransitionRecord

    /** Returns a page of [entity]/[entityId]'s transition history, most recent first. */
    fun findAll(entity: String, entityId: String, pageable: Pageable): Page<StatusTransitionRecord>
}
