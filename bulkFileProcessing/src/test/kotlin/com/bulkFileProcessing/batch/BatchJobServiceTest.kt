package com.bulkFileProcessing.batch

import com.bulkFileProcessing.jobstore.BulkJobRecord
import com.bulkFileProcessing.jobstore.BulkJobStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.repository.JobRepository
import kotlin.io.path.createTempFile

class BatchJobServiceTest {

    private val jobRepository: JobRepository = mockk()
    private val jobFactory: FileProcessingJobFactory = mockk()
    private val registry: FileProcessorRegistry = mockk()
    private val jobStore: BulkJobStore = mockk(relaxed = true)
    private val service = BatchJobService(jobRepository, jobFactory, registry, jobStore)

    @Test
    fun `throws and records a FAILED record when processorType is not registered`() {
        every { registry.find("unknown") } returns null
        val recordSlot = slot<BulkJobRecord>()
        every { jobStore.save(capture(recordSlot)) } returns Unit

        val file = createTempFile().toFile()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.launch(sourceFile = file, processorType = "unknown", jobId = "job-1")
        }

        assertEquals(
            "BatchJobService.launch: no FileProcessor registered for processorType='unknown' jobId=job-1",
            ex.message,
        )
        assertEquals(BatchStatus.FAILED, recordSlot.captured.status)
        assertEquals("job-1", recordSlot.captured.jobId)
        assertEquals("no FileProcessor registered for processorType='unknown'", recordSlot.captured.errorMessage)
        verify(exactly = 0) { jobFactory.create(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `saves a STARTED record before executing the job and returns jobId`() {
        val processor: FileProcessor<Any> = mockk(relaxed = true)
        every { registry.find("invoice-upload") } returns processor

        val job: Job = mockk()
        every { job.name } returns "job-job-1"
        every { job.execute(any()) } returns Unit

        val jobInstance: JobInstance = mockk()
        val jobExecution: JobExecution = mockk()
        every { jobExecution.status } returns BatchStatus.COMPLETED
        every { jobRepository.createJobInstance("job-job-1", any()) } returns jobInstance
        every { jobRepository.createJobExecution(jobInstance, any(), any()) } returns jobExecution

        val initialRecordSlot = slot<BulkJobRecord>()
        every {
            jobFactory.create(any(), any(), any(), any(), capture(initialRecordSlot))
        } returns job

        val recordSlot = slot<BulkJobRecord>()
        every { jobStore.save(capture(recordSlot)) } returns Unit

        val file = createTempFile(suffix = ".csv").toFile()
        val result = service.launch(
            sourceFile = file,
            processorType = "invoice-upload",
            jobId = "job-1",
            originalFileName = "invoices.csv",
        )

        assertEquals("job-1", result)
        assertEquals(BatchStatus.STARTED, recordSlot.captured.status)
        assertEquals("invoice-upload", recordSlot.captured.processorType)
        assertEquals("invoices.csv", recordSlot.captured.originalFileName)
        assertEquals(initialRecordSlot.captured.startedAt, recordSlot.captured.startedAt)
        assertEquals(recordSlot.captured, initialRecordSlot.captured)
        verify { jobStore.save(any()) }
    }

    @Test
    fun `a throwing jobStore does not prevent the job from running or launch from returning`() {
        val processor: FileProcessor<Any> = mockk(relaxed = true)
        every { registry.find("invoice-upload") } returns processor

        val job: Job = mockk()
        every { job.name } returns "job-job-2"
        every { job.execute(any()) } returns Unit

        val jobInstance: JobInstance = mockk()
        val jobExecution: JobExecution = mockk()
        every { jobExecution.status } returns BatchStatus.COMPLETED
        every { jobRepository.createJobInstance("job-job-2", any()) } returns jobInstance
        every { jobRepository.createJobExecution(jobInstance, any(), any()) } returns jobExecution
        every { jobFactory.create(any(), any(), any(), any(), any()) } returns job
        every { jobStore.save(any()) } throws RuntimeException("store down")

        val file = createTempFile(suffix = ".csv").toFile()
        val result = service.launch(sourceFile = file, processorType = "invoice-upload", jobId = "job-2")

        assertEquals("job-2", result)
        verify { job.execute(jobExecution) }
    }
}
