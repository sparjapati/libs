package com.bulkFileProcessing.mysql.mapping

import com.bulkFileProcessing.jobstore.BulkJobRecord
import com.bulkFileProcessing.mysql.entity.BulkJobRecordEntity

fun BulkJobRecordEntity.toDto(): BulkJobRecord = BulkJobRecord(
    jobId = jobId,
    processorType = processorType,
    status = status,
    writeCount = writeCount,
    skipCount = skipCount,
    resultFilePath = resultFilePath,
    errorMessage = errorMessage,
    originalFileName = originalFileName,
    startedAt = startedAt,
    completedAt = completedAt,
)

fun BulkJobRecord.toEntity(): BulkJobRecordEntity = BulkJobRecordEntity(
    jobId = jobId,
    processorType = processorType,
    status = status,
    writeCount = writeCount,
    skipCount = skipCount,
    resultFilePath = resultFilePath,
    errorMessage = errorMessage,
    originalFileName = originalFileName,
    startedAt = startedAt,
    completedAt = completedAt,
)
