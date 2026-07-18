package com.bulkFileProcessing.mysql

import com.bulkFileProcessing.jobstore.BulkJobRecord
import com.bulkFileProcessing.mysql.entity.BulkJobRecordEntity
import com.bulkFileProcessing.mysql.repository.BulkJobRecordJpaRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.Optional

class MysqlBulkJobStoreTest {

    private val repository: BulkJobRecordJpaRepository = mockk(relaxed = true)
    private val store = MysqlBulkJobStore(repository)

    @Test
    fun `findById returns dto when entity found`() {
        every { repository.findById("job-1") } returns Optional.of(sampleEntity())

        val record = store.findById("job-1")

        assertEquals("job-1", record?.jobId)
        assertEquals(BatchStatus.COMPLETED, record?.status)
    }

    @Test
    fun `findById returns null when entity not found`() {
        every { repository.findById("missing") } returns Optional.empty()
        assertNull(store.findById("missing"))
    }

    @Test
    fun `save persists the mapped entity`() {
        var saved: BulkJobRecordEntity? = null
        every { repository.save(any<BulkJobRecordEntity>()) } answers { saved = firstArg(); firstArg() }

        store.save(sampleRecord())

        assertEquals("job-1", saved?.jobId)
        assertEquals(BatchStatus.COMPLETED, saved?.status)
        assertEquals("invoices.csv", saved?.originalFileName)
    }

    @Test
    fun `findAll delegates to the filtered repository query and maps results`() {
        val pageable = PageRequest.of(0, 10)
        every {
            repository.findAllFiltered("invoice-upload", BatchStatus.COMPLETED, pageable)
        } returns PageImpl(listOf(sampleEntity()))

        val page = store.findAll(processorType = "invoice-upload", status = BatchStatus.COMPLETED, pageable = pageable)

        assertEquals(1L, page.totalElements)
        assertEquals("job-1", page.content.single().jobId)
        verify { repository.findAllFiltered("invoice-upload", BatchStatus.COMPLETED, pageable) }
    }

    private fun sampleEntity() = BulkJobRecordEntity(
        jobId = "job-1",
        processorType = "invoice-upload",
        status = BatchStatus.COMPLETED,
        writeCount = 10,
        skipCount = 0,
        resultFilePath = "/tmp/result.csv",
        errorMessage = null,
        originalFileName = "invoices.csv",
        startedAt = 1L,
        completedAt = 2L,
    )

    private fun sampleRecord() = BulkJobRecord(
        jobId = "job-1",
        processorType = "invoice-upload",
        status = BatchStatus.COMPLETED,
        writeCount = 10,
        skipCount = 0,
        resultFilePath = "/tmp/result.csv",
        errorMessage = null,
        originalFileName = "invoices.csv",
        startedAt = 1L,
        completedAt = 2L,
    )
}
