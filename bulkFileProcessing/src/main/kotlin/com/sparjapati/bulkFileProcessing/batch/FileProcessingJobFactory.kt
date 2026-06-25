package com.sparjapati.bulkFileProcessing.batch

import com.sparjapati.bulkFileProcessing.batch.reader.SpreadsheetItemReader
import org.slf4j.LoggerFactory
import java.io.File
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
 * The step is configured with a skip limit from [FileProcessor.skipLimit] (default: unlimited).
 * Each skipped row's error is recorded in [RowAccumulator] and written to the result
 * file by [BatchJobCompletionListener] after the job finishes.
 *
 * Registered only when [@EnableBulkFileProcessing][com.sparjapati.bulkFileProcessing.config.EnableBulkFileProcessing]
 * is present on a configuration class.
 *
 * @param jobRepository      Spring Batch job repository (auto-configured by Spring Boot).
 * @param transactionManager transaction manager used for chunk-based step execution.
 * @param handlerRegistry    registry of completion handlers; looked up by processorType after each job.
 */
class FileProcessingJobFactory(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val handlerRegistry: BulkJobCompletionHandlerRegistry,
    private val resultBaseDir: File,
) {

    /**
     * Builds a [Job] wired to the given [processor].
     *
     * The unchecked cast from [FileProcessor]<*> to [FileProcessor]<Any> is safe at
     * runtime because generic type parameters are erased by the JVM; the cast only
     * satisfies the Kotlin compiler so the [StepBuilder] chunk types align.
     *
     * @param processor        the domain-specific processor to use.
     * @param jobId            unique identifier for this run — used to name the Job and Step.
     * @param filePath         absolute path to the uploaded temp file.
     * @param fileType         "csv" or "xlsx".
     * @param originalFileName the original uploaded filename; used to name the result file.
     */
    @Suppress("UNCHECKED_CAST")
    fun create(
        processor: FileProcessor<*>,
        jobId: String,
        filePath: String,
        fileType: String,
        originalFileName: String,
    ): Job {
        val typedProcessor = processor as FileProcessor<Any>
        val accumulator = RowAccumulator()
        val fileWriter = ResultFileWriter(
            accumulator = accumulator,
            fileType = fileType,
            processorType = processor.processorType,
            originalFileName = originalFileName,
            resultBaseDir = resultBaseDir,
            declaredExtraColumns = processor.extraColumns,
        )
        val reader = SpreadsheetItemReader(filePath = filePath, fileType = fileType, accumulator = accumulator)
        val jobListener = BatchJobCompletionListener(
            writer = fileWriter,
            handler = handlerRegistry.find(processor.processorType),
        )

        val rowReader = typedProcessor.rowReader()
        val rowProcessor = typedProcessor.rowProcessor()

        val step: Step = StepBuilder("step-$jobId", jobRepository)
            .chunk<SpreadsheetRow, SpreadsheetRow>(typedProcessor.chunkSize)
            .transactionManager(transactionManager)
            .reader(reader)
            .writer { chunk ->
                val rows = chunk.items

                // Split rowReader results: record business failures immediately; pass only successes onward.
                val readerResults = rowReader(rows)
                val successMap = linkedMapOf<SpreadsheetRow, Any>()
                for ((row, result) in readerResults) {
                    when (result) {
                        is RowResult.Success -> successMap[row] = result.value
                        is RowResult.Failure -> accumulator.recordError(rowNumber = row.rowNumber, error = result.error)
                    }
                }

                if (successMap.isNotEmpty()) {
                    val processorResults = rowProcessor(successMap)
                    for ((row, result) in processorResults) {
                        when (result) {
                            is RowResult.Success -> accumulator.recordExtra(rowNumber = row.rowNumber, extra = result.value)
                            is RowResult.Failure -> accumulator.recordError(rowNumber = row.rowNumber, error = result.error)
                        }
                    }
                }
            }
            .faultTolerant()
            .skip(Exception::class.java)
            .skipLimit(typedProcessor.skipLimit)
            .skipListener(RowSkipListener(jobId = jobId, accumulator = accumulator))
            .build()

        return JobBuilder("job-$jobId", jobRepository)
            .start(step)
            .listener(jobListener)
            .build()
    }
}

/**
 * Records per-row errors into [RowAccumulator] when Spring Batch skips a row
 * due to an exception during processing or writing.
 *
 * @param jobId       used as a correlation key in warning log messages.
 * @param accumulator the shared [RowAccumulator] for this job run.
 */
private class RowSkipListener(
    private val jobId: String,
    private val accumulator: RowAccumulator,
) : SkipListener<SpreadsheetRow, SpreadsheetRow> {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RowSkipListener::class.java)
    }

    override fun onSkipInRead(t: Throwable) {
        LOGGER.warn("jobId={} — row skipped on read: {}", jobId, t.message)
    }

    override fun onSkipInProcess(item: SpreadsheetRow, t: Throwable) = Unit

    override fun onSkipInWrite(item: SpreadsheetRow, t: Throwable) {
        val error = t.message ?: t::class.simpleName ?: "unknown error"
        LOGGER.warn("jobId={} — row #{} skipped while processing: {}", jobId, item.rowNumber, error)
        accumulator.recordError(rowNumber = item.rowNumber, error = error)
    }
}
