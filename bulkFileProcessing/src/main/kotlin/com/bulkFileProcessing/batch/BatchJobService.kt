package com.bulkFileProcessing.batch

import com.bulkFileProcessing.batch.BatchJobCompletionListener.Companion.JOB_PARAM_JOB_ID
import com.bulkFileProcessing.batch.BatchJobCompletionListener.Companion.JOB_PARAM_PROCESSOR_TYPE
import com.bulkFileProcessing.jobstore.BulkJobRecord
import com.bulkFileProcessing.jobstore.BulkJobStore
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.infrastructure.item.ExecutionContext
import java.io.File
import java.util.UUID

/**
 * Orchestrates bulk file processing: saves the uploaded file to a temp location,
 * builds the Spring Batch [Job] via [FileProcessingJobFactory], and executes it
 * synchronously in the calling thread.
 *
 * The caller is responsible for submitting this to a background executor. This
 * keeps threading and context-propagation concerns (e.g. MDC) out of the library.
 *
 * Uses [JobRepository.createJobExecution] + [org.springframework.batch.core.job.Job.execute]
 * directly instead of the deprecated [org.springframework.batch.core.launch.JobLauncher] API
 * (deprecated in Spring Batch 6.0).
 *
 * Every job's lifecycle is also recorded via [jobStore] — a FAILED record before throwing
 * for an unregistered [processorType], a STARTED record before the job executes, and a final
 * record (written by [BatchJobCompletionListener]) after it finishes. A [jobStore] failure is
 * logged and swallowed — it must never abort the actual file-processing job.
 */
class BatchJobService(
    private val jobRepository: JobRepository,
    private val jobFactory: FileProcessingJobFactory,
    private val registry: FileProcessorRegistry,
    private val jobStore: BulkJobStore,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(BatchJobService::class.java)
    }

    /**
     * Runs the batch job synchronously against [sourceFile].
     *
     * The caller is responsible for writing the uploaded content to [sourceFile] before
     * dispatching this call to a background thread — [MultipartFile] cleanup happens on
     * the HTTP thread and the underlying stream may be gone by the time a background thread
     * would read it.
     *
     * Must be called from a background thread — this method blocks until the job completes.
     * Use [jobId] to correlate logs with the response returned to the HTTP caller.
     *
     * @param sourceFile       pre-written file on disk containing the CSV or XLSX content.
     * @param processorType    identifier that maps to a registered [FileProcessor].
     * @param jobId            caller-supplied identifier so the response can be built before this
     *                         method is dispatched to a background thread. Defaults to a random
     *                         UUID when the caller doesn't need to know it up front; the resolved
     *                         value is returned so it can still be recovered after the call.
     * @param originalFileName the original uploaded filename (e.g. from [MultipartFile.originalFilename]);
     *                         used to name the result file. Defaults to the source file's own name.
     * @return the [jobId] that was used for this run (either the caller-supplied value or the
     *         generated UUID).
     * @throws IllegalArgumentException if no [FileProcessor] is registered for [processorType].
     */
    fun launch(
        sourceFile: File,
        processorType: String,
        jobId: String = UUID.randomUUID().toString(),
        originalFileName: String = sourceFile.name,
    ): String {
        val startedAt = System.currentTimeMillis()
        val processor = registry.find(processorType)
        if (processor == null) {
            val errorMessage = "no FileProcessor registered for processorType='$processorType'"
            trySave(
                BulkJobRecord(
                    jobId = jobId,
                    processorType = processorType,
                    status = BatchStatus.FAILED,
                    writeCount = 0,
                    skipCount = 0,
                    resultFilePath = null,
                    errorMessage = errorMessage,
                    originalFileName = originalFileName,
                    startedAt = startedAt,
                    completedAt = startedAt,
                ),
            )
            throw IllegalArgumentException("BatchJobService.launch: $errorMessage jobId=$jobId")
        }

        trySave(
            BulkJobRecord(
                jobId = jobId,
                processorType = processorType,
                status = BatchStatus.STARTED,
                writeCount = 0,
                skipCount = 0,
                resultFilePath = null,
                errorMessage = null,
                originalFileName = originalFileName,
                startedAt = startedAt,
                completedAt = null,
            ),
        )

        val extension = sourceFile.extension.lowercase().ifEmpty { "csv" }
        LOGGER.info(
            "Starting bulk job jobId={} processorType={} fileType={} fileSizeBytes={} originalFileName={}",
            jobId, processorType, extension, sourceFile.length(), originalFileName,
        )

        val params = JobParametersBuilder()
            .addString(JOB_PARAM_JOB_ID, jobId)
            .addString(JOB_PARAM_PROCESSOR_TYPE, processorType)
            .addString("filePath", sourceFile.absolutePath)
            .addString("fileType", extension)
            .addLong("startedAt", startedAt)
            .toJobParameters()

        val job = jobFactory.create(
            processor = processor,
            jobId = jobId,
            filePath = sourceFile.absolutePath,
            fileType = extension,
            originalFileName = originalFileName,
            startedAt = startedAt,
        )

        val jobInstance = jobRepository.createJobInstance(job.name, params)
        val jobExecution = jobRepository.createJobExecution(jobInstance, params, ExecutionContext())
        job.execute(jobExecution)
        LOGGER.info(
            "Bulk job finished jobId={} processorType={} status={}",
            jobId, processorType, jobExecution.status,
        )
        return jobId
    }

    private fun trySave(record: BulkJobRecord) {
        try {
            jobStore.save(record)
        } catch (ex: Exception) {
            LOGGER.error(
                "BulkJobStore.save failed for jobId={} processorType={} status={} — job continues, record not persisted",
                record.jobId, record.processorType, record.status, ex,
            )
        }
    }
}
