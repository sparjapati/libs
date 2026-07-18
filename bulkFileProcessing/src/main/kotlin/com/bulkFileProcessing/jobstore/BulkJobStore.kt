package com.bulkFileProcessing.jobstore

import com.bulkFileProcessing.batch.ProcessorType
import org.springframework.batch.core.BatchStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Persists and queries [BulkJobRecord]s for jobs launched via
 * [com.bulkFileProcessing.batch.BatchJobService.launch].
 *
 * The default implementation ([NoOpBulkJobStore]) discards every record. Set
 * `bulk.job-store.type: in-memory` to use [InMemoryBulkJobStore], or add a store adapter
 * module (e.g. `bulkFileProcessing-mysql`) for durable, queryable storage — the adapter
 * takes precedence automatically when present on the classpath.
 */
interface BulkJobStore {

    /** Upserts [record], keyed by [BulkJobRecord.jobId]. */
    fun save(record: BulkJobRecord)

    /** Returns the record for [jobId], or `null` if none exists. */
    fun findById(jobId: JobId): BulkJobRecord?

    /**
     * Returns a page of records, optionally filtered by [processorType] and/or [status].
     * Pass `null` for either filter to match all values.
     */
    fun findAll(processorType: ProcessorType? = null, status: BatchStatus? = null, pageable: Pageable): Page<BulkJobRecord>
}
