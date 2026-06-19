package com.sparjapati.bulkFileProcessing.batch

import com.sparjapati.bulkFileProcessing.batch.reader.SpreadsheetItemReader
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.listener.SkipListener
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.transaction.PlatformTransactionManager

/**
 * Factory that assembles a fault-tolerant Spring Batch [Job] for a specific [FileProcessor]
 * at runtime.
 *
 * Each call produces a uniquely named Job and Step (using [jobId]), so concurrent uploads
 * of the same [FileProcessor.processorType] run as independent jobs without conflicting
 * in the [JobRepository].
 *
 * The step is configured with unlimited skip so individual row errors do not abort the job.
 * Each skipped row's error is recorded in [RowResultCollector] and written to the result
 * file by [BatchJobCompletionListener] after the job finishes.
 *
 * Registered only when [@EnableBulkFileProcessing][com.sparjapati.bulkFileProcessing.config.EnableBulkFileProcessing]
 * is present on a configuration class.
 *
 * @param jobRepository      Spring Batch job repository (auto-configured by Spring Boot).
 * @param transactionManager transaction manager used for chunk-based step execution.
 */
class FileProcessingJobFactory(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
) {

    /**
     * Builds a [Job] wired to the given [processor].
     *
     * The unchecked cast from [FileProcessor]<*> to [FileProcessor]<Any> is safe at
     * runtime because generic type parameters are erased by the JVM; the cast only
     * satisfies the Kotlin compiler so the [StepBuilder] chunk types align.
     *
     * @param processor  the domain-specific processor to use.
     * @param jobId      unique identifier for this run — used to name the Job and Step.
     * @param filePath   absolute path to the uploaded temp file.
     * @param fileType   "csv" or "xlsx".
     */
    @Suppress("UNCHECKED_CAST")
    fun create(
        processor: FileProcessor<*>,
        jobId: String,
        filePath: String,
        fileType: String,
    ): Job {
        val typedProcessor = processor as FileProcessor<Any>
        val collector = RowResultCollector(fileType = fileType)
        val reader = SpreadsheetItemReader(filePath = filePath, fileType = fileType, collector = collector)
        val jobListener = BatchJobCompletionListener(collector = collector, processor = processor)

        val step: Step = StepBuilder("step-$jobId", jobRepository)
            .chunk<SpreadsheetRow, Any>(typedProcessor.chunkSize)
            .transactionManager(transactionManager)
            .reader(reader)
            .processor(typedProcessor.rowReader())
            .writer(typedProcessor.rowProcessor())
            .faultTolerant()
            .skip(Exception::class.java)
            .skipLimit(Long.MAX_VALUE)
            .skipListener(RowSkipListener(jobId = jobId, collector = collector))
            .build()

        return JobBuilder("job-$jobId", jobRepository)
            .start(step)
            .listener(jobListener)
            .build()
    }
}

/**
 * Records per-row errors into [RowResultCollector] when Spring Batch skips a row
 * due to an exception during processing or writing.
 *
 * @param jobId     used as a correlation key in warning log messages.
 * @param collector the shared [RowResultCollector] for this job run.
 */
private class RowSkipListener(
    private val jobId: String,
    private val collector: RowResultCollector,
) : SkipListener<SpreadsheetRow, Any> {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RowSkipListener::class.java)
    }

    override fun onSkipInRead(t: Throwable) {
        LOGGER.warn("jobId={} — row skipped on read: {}", jobId, t.message)
    }

    override fun onSkipInProcess(item: SpreadsheetRow, t: Throwable) {
        val error = t.message ?: t::class.simpleName ?: "unknown error"
        LOGGER.warn("jobId={} — row #{} skipped on process: {}", jobId, item.rowNumber, error)
        collector.recordError(rowNumber = item.rowNumber, error = error)
    }

    override fun onSkipInWrite(item: Any, t: Throwable) {
        val error = t.message ?: t::class.simpleName ?: "unknown error"
        LOGGER.warn("jobId={} — item skipped on write: {} | error: {}", jobId, item, error)
        // item here is the domain object T, not SpreadsheetRow — row number not available
    }
}
