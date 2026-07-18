package com.bulkFileProcessing.jobstore

import com.bulkFileProcessing.batch.ProcessorType
import org.springframework.batch.core.BatchStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local [BulkJobStore] — records are kept in memory only, lost on restart, and
 * not shared across instances. Enabled via `bulk.job-store.type: in-memory`.
 */
class InMemoryBulkJobStore : BulkJobStore {

    private val records = ConcurrentHashMap<JobId, BulkJobRecord>()

    override fun save(record: BulkJobRecord) {
        records[record.jobId] = record
    }

    override fun findById(jobId: JobId): BulkJobRecord? = records[jobId]

    override fun findAll(processorType: ProcessorType?, status: BatchStatus?, pageable: Pageable): Page<BulkJobRecord> {
        val filtered = records.values
            .filter { processorType == null || it.processorType == processorType }
            .filter { status == null || it.status == status }
            .sortedByDescending { it.startedAt }

        val start = pageable.offset.toInt().coerceIn(0, filtered.size)
        val end = (start + pageable.pageSize).coerceIn(start, filtered.size)
        return PageImpl(filtered.subList(start, end), pageable, filtered.size.toLong())
    }
}
