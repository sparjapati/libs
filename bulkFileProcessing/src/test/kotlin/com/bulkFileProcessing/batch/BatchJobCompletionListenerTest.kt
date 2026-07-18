package com.bulkFileProcessing.batch

import com.bulkFileProcessing.events.BulkJobCompletionHandler
import com.bulkFileProcessing.jobstore.BulkJobRecord
import com.bulkFileProcessing.jobstore.BulkJobStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.JobInstance
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.step.StepExecution

class BatchJobCompletionListenerTest {

    private val writer: ResultFileWriter = mockk()
    private val handler: BulkJobCompletionHandler = mockk(relaxed = true)
    private val jobStore: BulkJobStore = mockk(relaxed = true)

    private fun jobExecution(status: BatchStatus, failureMessage: String? = null): JobExecution {
        val params = JobParametersBuilder()
            .addString(BatchJobCompletionListener.JOB_PARAM_JOB_ID, "job-1")
            .addString(BatchJobCompletionListener.JOB_PARAM_PROCESSOR_TYPE, "invoice-upload")
            .toJobParameters()
        val jobInstance = JobInstance(1L, "job-job-1")
        val execution = JobExecution(1L, jobInstance, params)
        execution.status = status
        if (failureMessage != null) {
            execution.addFailureException(RuntimeException(failureMessage))
        }
        val step = StepExecution("step-job-1", execution)
        step.writeCount = 5
        step.writeSkipCount = 1
        execution.addStepExecution(step)
        return execution
    }

    private fun initialRecord(): BulkJobRecord = BulkJobRecord(
        jobId = "job-1",
        processorType = "invoice-upload",
        status = BatchStatus.STARTED,
        writeCount = 0,
        skipCount = 0,
        resultFilePath = null,
        errorMessage = null,
        originalFileName = "invoices.csv",
        startedAt = 100L,
        completedAt = null,
    )

    @Test
    fun `saves a COMPLETED record with counts and result file`() {
        every { writer.write() } returns "/tmp/result.csv"
        val listener = BatchJobCompletionListener(writer, handler, jobStore, initialRecord())
        val recordSlot = slot<BulkJobRecord>()
        every { jobStore.save(capture(recordSlot)) } returns Unit

        listener.afterJob(jobExecution(BatchStatus.COMPLETED))

        val record = recordSlot.captured
        assertEquals("job-1", record.jobId)
        assertEquals(BatchStatus.COMPLETED, record.status)
        assertEquals(5L, record.writeCount)
        assertEquals(1L, record.skipCount)
        assertEquals("/tmp/result.csv", record.resultFilePath)
        assertNull(record.errorMessage)
        assertEquals("invoices.csv", record.originalFileName)
        assertEquals(100L, record.startedAt)
    }

    @Test
    fun `saves a FAILED record with the failure message`() {
        every { writer.write() } returns null
        val listener = BatchJobCompletionListener(writer, handler, jobStore, initialRecord())
        val recordSlot = slot<BulkJobRecord>()
        every { jobStore.save(capture(recordSlot)) } returns Unit

        listener.afterJob(jobExecution(BatchStatus.FAILED, failureMessage = "DB unavailable"))

        assertEquals(BatchStatus.FAILED, recordSlot.captured.status)
        assertEquals("DB unavailable", recordSlot.captured.errorMessage)
    }

    @Test
    fun `a throwing jobStore does not prevent the completion handler from running`() {
        every { writer.write() } returns "/tmp/result.csv"
        every { jobStore.save(any()) } throws RuntimeException("store down")
        val listener = BatchJobCompletionListener(writer, handler, jobStore, initialRecord())

        listener.afterJob(jobExecution(BatchStatus.COMPLETED))

        verify { handler.onJobCompleted(any()) }
    }
}
