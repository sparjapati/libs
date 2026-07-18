package com.bulkFileProcessing.mysql.repository

import com.bulkFileProcessing.batch.ProcessorType
import com.bulkFileProcessing.jobstore.JobId
import com.bulkFileProcessing.mysql.entity.BulkJobRecordEntity
import org.springframework.batch.core.BatchStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BulkJobRecordJpaRepository : JpaRepository<BulkJobRecordEntity, JobId> {

    @Query(
        """
        select r from bulkJobRecord r
        where (:processorType is null or r.processorType = :processorType)
          and (:status is null or r.status = :status)
        """,
    )
    fun findAllFiltered(
        @Param("processorType") processorType: ProcessorType?,
        @Param("status") status: BatchStatus?,
        pageable: Pageable,
    ): Page<BulkJobRecordEntity>
}
