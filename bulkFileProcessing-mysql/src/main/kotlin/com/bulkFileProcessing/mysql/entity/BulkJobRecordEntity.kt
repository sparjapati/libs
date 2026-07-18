package com.bulkFileProcessing.mysql.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.springframework.batch.core.BatchStatus

@Entity(name = "bulkJobRecord")
@Table(
    name = "bulk_job_record",
    indexes = [Index(name = "idx_bulk_job_record_processor_type_status", columnList = "processor_type,status")],
)
class BulkJobRecordEntity {
    @Id
    @Column(name = "job_id", length = 255)
    var jobId: String = ""
        private set

    @Column(name = "processor_type", nullable = false)
    var processorType: String = ""
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: BatchStatus = BatchStatus.STARTING
        private set

    @Column(name = "write_count", nullable = false)
    var writeCount: Long = 0
        private set

    @Column(name = "skip_count", nullable = false)
    var skipCount: Long = 0
        private set

    @Column(name = "result_file_path")
    var resultFilePath: String? = null
        private set

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null
        private set

    @Column(name = "original_file_name", nullable = false)
    var originalFileName: String = ""
        private set

    @Column(name = "started_at", nullable = false)
    var startedAt: Long = 0
        private set

    @Column(name = "completed_at")
    var completedAt: Long? = null
        private set

    constructor(
        jobId: String,
        processorType: String,
        status: BatchStatus,
        writeCount: Long,
        skipCount: Long,
        resultFilePath: String?,
        errorMessage: String?,
        originalFileName: String,
        startedAt: Long,
        completedAt: Long?,
    ) {
        this.jobId = jobId
        this.processorType = processorType
        this.status = status
        this.writeCount = writeCount
        this.skipCount = skipCount
        this.resultFilePath = resultFilePath
        this.errorMessage = errorMessage
        this.originalFileName = originalFileName
        this.startedAt = startedAt
        this.completedAt = completedAt
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BulkJobRecordEntity
        return jobId == other.jobId
    }

    override fun hashCode(): Int = jobId.hashCode()
}
