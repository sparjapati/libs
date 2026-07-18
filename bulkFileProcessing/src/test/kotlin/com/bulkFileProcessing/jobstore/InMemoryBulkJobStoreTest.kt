package com.bulkFileProcessing.jobstore

import com.bulkFileProcessing.batch.ProcessorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.data.domain.PageRequest

class InMemoryBulkJobStoreTest {

    private val store = InMemoryBulkJobStore()

    @Test
    fun `save then findById returns the record`() {
        store.save(record(jobId = "job-1", status = BatchStatus.STARTED))
        assertEquals(BatchStatus.STARTED, store.findById("job-1")?.status)
    }

    @Test
    fun `save upserts by jobId`() {
        store.save(record(jobId = "job-1", status = BatchStatus.STARTED))
        store.save(record(jobId = "job-1", status = BatchStatus.COMPLETED))

        val all = store.findAll(pageable = PageRequest.of(0, 10))

        assertEquals(1L, all.totalElements)
        assertEquals(BatchStatus.COMPLETED, store.findById("job-1")?.status)
    }

    @Test
    fun `findById returns null for unknown jobId`() {
        assertNull(store.findById("missing"))
    }

    @Test
    fun `findAll filters by processorType and status`() {
        store.save(record(jobId = "job-1", processorType = "invoice-upload", status = BatchStatus.COMPLETED))
        store.save(record(jobId = "job-2", processorType = "invoice-upload", status = BatchStatus.FAILED))
        store.save(record(jobId = "job-3", processorType = "user-upload", status = BatchStatus.COMPLETED))

        val page = store.findAll(
            processorType = "invoice-upload",
            status = BatchStatus.COMPLETED,
            pageable = PageRequest.of(0, 10),
        )

        assertEquals(1L, page.totalElements)
        assertEquals("job-1", page.content.single().jobId)
    }

    @Test
    fun `findAll paginates results`() {
        repeat(5) { i -> store.save(record(jobId = "job-$i", status = BatchStatus.COMPLETED)) }

        val firstPage = store.findAll(pageable = PageRequest.of(0, 2))
        val secondPage = store.findAll(pageable = PageRequest.of(1, 2))

        assertEquals(5L, firstPage.totalElements)
        assertEquals(2, firstPage.content.size)
        assertEquals(2, secondPage.content.size)
    }

    private fun record(
        jobId: JobId,
        processorType: ProcessorType = "invoice-upload",
        status: BatchStatus,
    ) = BulkJobRecord(
        jobId = jobId,
        processorType = processorType,
        status = status,
        writeCount = 0,
        skipCount = 0,
        resultFilePath = null,
        errorMessage = null,
        originalFileName = "file.csv",
        startedAt = 1L,
        completedAt = null,
    )
}
