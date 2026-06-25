package com.bulkFileProcessing.batch

import com.bulkFileProcessing.batch.BatchJobCompletionListener.Companion.JOB_PARAM_JOB_ID
import com.bulkFileProcessing.batch.BatchJobCompletionListener.Companion.JOB_PARAM_PROCESSOR_TYPE
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.infrastructure.item.ExecutionContext
import java.io.File

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
 */
class BatchJobService(
    private val jobRepository: JobRepository,
    private val jobFactory: FileProcessingJobFactory,
    private val registry: FileProcessorRegistry,
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
     *                         method is dispatched to a background thread.
     * @param originalFileName the original uploaded filename (e.g. from [MultipartFile.originalFilename]);
     *                         used to name the result file. Defaults to the source file's own name.
     */
    fun launch(
        sourceFile: File,
        processorType: String,
        jobId: String,
        originalFileName: String = sourceFile.name,
    ) {
        val processor = registry.find(processorType)
        if (processor == null) {
            LOGGER.error(
                "No FileProcessor registered for processorType='{}' jobId={} — job not started",
                processorType, jobId,
            )
            return
        }
        val extension = sourceFile.extension.lowercase().ifEmpty { "csv" }

        val params = JobParametersBuilder()
            .addString(JOB_PARAM_JOB_ID, jobId)
            .addString(JOB_PARAM_PROCESSOR_TYPE, processorType)
            .addString("filePath", sourceFile.absolutePath)
            .addString("fileType", extension)
            .addLong("startedAt", System.currentTimeMillis())
            .toJobParameters()

        val job = jobFactory.create(
            processor = processor,
            jobId = jobId,
            filePath = sourceFile.absolutePath,
            fileType = extension,
            originalFileName = originalFileName,
        )

        LOGGER.info("Starting bulk job jobId={} processorType={}", jobId, processorType)
        val jobInstance = jobRepository.createJobInstance(job.name, params)
        val jobExecution = jobRepository.createJobExecution(jobInstance, params, ExecutionContext())
        job.execute(jobExecution)
    }
}
