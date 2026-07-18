package com.bulkFileProcessing.jobstore

import org.springframework.batch.core.BatchStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Default [BulkJobStore] when no adapter or config opts into persistence.
 *
 * `save()` discards the record; `findById()`/`findAll()` always return nothing. Existing
 * consumers of `bulkFileProcessing` get this automatically and see no behavior change —
 * job records simply aren't queryable until you configure one of the options below.
 *
 * **To enable querying, pick one:**
 * - Set `bulk.job-store.type: in-memory` in `application.yml` — job records are kept in a
 *   process-local map. Lost on restart, not shared across instances. Good for a
 *   single-instance app or local development.
 * - Add `implementation("com.sparjapati:bulkFileProcessing-mysql:0.0.1")` — job records
 *   are persisted to MySQL via JPA and survive restarts / are visible across instances.
 *   Takes precedence automatically if present on the classpath; no other configuration
 *   needed beyond an existing `DataSource`.
 */
class NoOpBulkJobStore : BulkJobStore {
    override fun save(record: BulkJobRecord) = Unit
    override fun findById(jobId: String): BulkJobRecord? = null
    override fun findAll(processorType: String?, status: BatchStatus?, pageable: Pageable): Page<BulkJobRecord> =
        Page.empty(pageable)
}
