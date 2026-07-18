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
    name = "bulkJobRecord",
    indexes = [Index(name = "idx_bulk_job_record_processor_type_status", columnList = "processorType,status")],
)
class BulkJobRecordEntity {
    @Id
    @Column(name = "jobId", length = 255)
    var jobId: String = ""
        private set

    @Column(name = "processorType", nullable = false)
    var processorType: String = ""
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: BatchStatus = BatchStatus.STARTING
        private set

    @Column(name = "writeCount", nullable = false)
    var writeCount: Long = 0
        private set

    @Column(name = "skipCount", nullable = false)
    var skipCount: Long = 0
        private set

    @Column(name = "resultFilePath")
    var resultFilePath: String? = null
        private set

    @Column(name = "errorMessage", columnDefinition = "TEXT")
    var errorMessage: String? = null
        private set

    @Column(name = "originalFileName", nullable = false)
    var originalFileName: String = ""
        private set

    @Column(name = "startedAt", nullable = false)
    var startedAt: Long = 0
        private set

    @Column(name = "completedAt")
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

    /** Marks this entity [BatchStatus.FAILED] with [errorMessage] and [completedAt] set. */
    fun markFailed(errorMessage: String, completedAt: Long) {
        status = BatchStatus.FAILED
        this.errorMessage = errorMessage
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
