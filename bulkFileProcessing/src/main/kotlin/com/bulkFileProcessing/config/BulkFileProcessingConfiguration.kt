package com.bulkFileProcessing.config

import com.bulkFileProcessing.batch.BatchJobService
import com.bulkFileProcessing.batch.BulkJobCompletionHandlerRegistry
import com.bulkFileProcessing.batch.BulkTempFileCleanupRunner
import com.bulkFileProcessing.batch.FileProcessor
import com.bulkFileProcessing.batch.FileProcessingJobFactory
import com.bulkFileProcessing.batch.FileProcessorRegistry
import com.bulkFileProcessing.events.BulkJobCompletionHandler
import com.bulkFileProcessing.jobstore.BulkJobStore
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.io.File

/**
 * Registers the bulk file processing beans. Imported via [@EnableBulkFileProcessing].
 *
 * **Configuration properties:**
 * - `bulk.result-base-dir` — root directory for result files.
 *   Defaults to `{java.io.tmpdir}/bulk-results` if not set.
 *   Result files are written to `{resultBaseDir}/{processorType}/{date}/result-{filename}.{ext}`.
 * - `bulk.job-store.type: in-memory` — opt into `InMemoryBulkJobStore` when no store adapter
 *   module is present (see [com.bulkFileProcessing.jobstore.BulkJobStoreDefaultsAutoConfiguration]).
 */
@Configuration
class BulkFileProcessingConfiguration {

    @Bean
    fun fileProcessorRegistry(processors: List<FileProcessor<*>>): FileProcessorRegistry =
        FileProcessorRegistry(processors = processors)

    @Bean
    fun bulkJobCompletionHandlerRegistry(handlers: List<BulkJobCompletionHandler>): BulkJobCompletionHandlerRegistry =
        BulkJobCompletionHandlerRegistry(handlers = handlers)

    @Bean
    fun fileProcessingJobFactory(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        handlerRegistry: BulkJobCompletionHandlerRegistry,
        jobStore: BulkJobStore,
        @Value("\${bulk.result-base-dir:}") resultBaseDirPath: String,
    ): FileProcessingJobFactory {
        val resultBaseDir = if (resultBaseDirPath.isBlank())
            File(System.getProperty("java.io.tmpdir"), "bulk-processors-results")
        else
            File(resultBaseDirPath)

        return FileProcessingJobFactory(
            jobRepository = jobRepository,
            transactionManager = transactionManager,
            handlerRegistry = handlerRegistry,
            jobStore = jobStore,
            resultBaseDir = resultBaseDir,
        )
    }

    @Bean
    fun bulkTempFileCleanupRunner(): BulkTempFileCleanupRunner = BulkTempFileCleanupRunner()

    @Bean
    fun batchJobService(
        jobRepository: JobRepository,
        jobFactory: FileProcessingJobFactory,
        registry: FileProcessorRegistry,
        jobStore: BulkJobStore,
    ): BatchJobService = BatchJobService(
        jobRepository = jobRepository,
        jobFactory = jobFactory,
        registry = registry,
        jobStore = jobStore,
    )
}
