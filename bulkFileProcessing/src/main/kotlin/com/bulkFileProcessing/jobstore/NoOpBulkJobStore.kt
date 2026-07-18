package com.bulkFileProcessing.jobstore

import com.bulkFileProcessing.batch.ProcessorType
import org.springframework.batch.core.BatchStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Default [BulkJobStore] when no adapter or config opts into persistence.
 *
 * `save()` discards the record. `findById()`/`findAll()` return nothing.
 *
 * **To enable querying, pick one:**
 * - Set `bulk.job-store.type: in-memory` in `application.yml` — job records are kept in a
 *   process-local map, lost on restart, not shared across instances.
 * - Add `implementation("com.sparjapati:bulkFileProcessing-mysql:0.0.1")` — job records
 *   are persisted to MySQL via JPA, surviving restarts and visible across instances.
 *   Takes precedence automatically when present on the classpath.
 */
class NoOpBulkJobStore : BulkJobStore {
    override fun save(record: BulkJobRecord) = Unit
    override fun findById(jobId: JobId): BulkJobRecord? = null
    override fun findAll(processorType: ProcessorType?, status: BatchStatus?, pageable: Pageable): Page<BulkJobRecord> =
        Page.empty(pageable)
}
