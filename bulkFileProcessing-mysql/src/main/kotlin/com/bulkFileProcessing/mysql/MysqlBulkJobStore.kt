package com.bulkFileProcessing.mysql

import com.bulkFileProcessing.jobstore.BulkJobRecord
import com.bulkFileProcessing.jobstore.BulkJobStore
import com.bulkFileProcessing.mysql.mapping.toDto
import com.bulkFileProcessing.mysql.mapping.toEntity
import com.bulkFileProcessing.mysql.repository.BulkJobRecordJpaRepository
import org.springframework.batch.core.BatchStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

open class MysqlBulkJobStore(
    private val repository: BulkJobRecordJpaRepository,
) : BulkJobStore {

    @Transactional
    override fun save(record: BulkJobRecord) {
        repository.save(record.toEntity())
    }

    @Transactional(readOnly = true)
    override fun findById(jobId: String): BulkJobRecord? =
        repository.findById(jobId).getOrNull()?.toDto()

    @Transactional(readOnly = true)
    override fun findAll(processorType: String?, status: BatchStatus?, pageable: Pageable): Page<BulkJobRecord> =
        repository.findAllFiltered(processorType, status, pageable).map { it.toDto() }
}
