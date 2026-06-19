package com.sparjapati.bulkFileProcessing.batch

import com.sparjapati.bulkFileProcessing.batch.BatchJobCompletionListener.Companion.JOB_PARAM_JOB_ID
import com.sparjapati.bulkFileProcessing.batch.BatchJobCompletionListener.Companion.JOB_PARAM_PROCESSOR_TYPE
import com.sparjapati.bulkFileProcessing.dtos.BulkUploadResponse
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Orchestrates bulk file processing: saves the uploaded file to a temp location,
 * builds the Spring Batch [Job] via [FileProcessingJobFactory], submits it to a
 * background thread pool, and returns immediately with a [jobId].
 *
 * The temp file is deleted in a `finally` block after the job finishes (or fails),
 * so it is not left around after the HTTP request completes.
 *
 * Uses [JobRepository.createJobExecution] + [org.springframework.batch.core.job.Job.execute]
 * directly instead of the deprecated [org.springframework.batch.core.launch.JobLauncher] API
 * (deprecated in Spring Batch 6.0).
 */
class BatchJobService(
    private val jobRepository: JobRepository,
    private val jobFactory: FileProcessingJobFactory,
    private val registry: FileProcessorRegistry,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(BatchJobService::class.java)
        private val executor = Executors.newFixedThreadPool(10)
    }

    /**
     * Accepts an uploaded file, launches a batch job asynchronously, and returns a
     * [BulkUploadResponse] with the [jobId] for tracing.
     *
     * @param file          the uploaded CSV or XLSX [MultipartFile].
     * @param processorType identifier that maps to a registered [FileProcessor].
     * @return [BulkUploadResponse] containing the [jobId].
     * @throws IllegalArgumentException if [processorType] is not registered.
     */
    fun launch(
        file: MultipartFile,
        processorType: String,
    ): BulkUploadResponse {
        val processor = registry.get(processorType)

        val extension = file.originalFilename
            ?.substringAfterLast('.', missingDelimiterValue = "csv")
            ?.lowercase()
            ?: "csv"

        val jobId = UUID.randomUUID().toString()
        val tempFile = Files.createTempFile("bulk-$processorType-$jobId", ".$extension").toFile()
        file.transferTo(tempFile)

        val params = JobParametersBuilder()
            .addString(JOB_PARAM_JOB_ID, jobId)
            .addString(JOB_PARAM_PROCESSOR_TYPE, processorType)
            .addString("filePath", tempFile.absolutePath)
            .addString("fileType", extension)
            .addLong("startedAt", System.currentTimeMillis())
            .toJobParameters()

        val job = jobFactory.create(
            processor = processor,
            jobId = jobId,
            filePath = tempFile.absolutePath,
            fileType = extension,
        )

        executor.submit {
            try {
                LOGGER.info("Starting bulk job jobId={} processorType={}", jobId, processorType)
                val jobInstance = jobRepository.createJobInstance(job.name, params)
                val jobExecution = jobRepository.createJobExecution(jobInstance, params, ExecutionContext())
                job.execute(jobExecution)
            } finally {
                if (tempFile.delete()) {
                    LOGGER.info("Deleted temp file for jobId={}", jobId)
                } else {
                    LOGGER.warn(
                        "Failed to delete temp file '{}' for jobId={}",
                        tempFile.absolutePath, jobId,
                    )
                }
            }
        }

        return BulkUploadResponse(
            jobId = jobId,
            message = "Processing started for '$processorType'. You will receive a notification on completion.",
        )
    }
}
