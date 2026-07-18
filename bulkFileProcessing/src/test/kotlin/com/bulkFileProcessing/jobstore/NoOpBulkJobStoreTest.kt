package com.bulkFileProcessing.jobstore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.data.domain.PageRequest

class NoOpBulkJobStoreTest {

    private val store = NoOpBulkJobStore()

    @Test
    fun `save is a no-op`() {
        store.save(sampleRecord())
        assertNull(store.findById("job-1"))
    }

    @Test
    fun `findById always returns null`() {
        assertNull(store.findById("anything"))
    }

    @Test
    fun `findAll always returns an empty page`() {
        val page = store.findAll(pageable = PageRequest.of(0, 10))
        assertEquals(0L, page.totalElements)
    }

    private fun sampleRecord() = BulkJobRecord(
        jobId = "job-1",
        processorType = "invoice-upload",
        status = BatchStatus.STARTED,
        writeCount = 0,
        skipCount = 0,
        resultFilePath = null,
        errorMessage = null,
        originalFileName = "file.csv",
        startedAt = 1L,
        completedAt = null,
    )
}
