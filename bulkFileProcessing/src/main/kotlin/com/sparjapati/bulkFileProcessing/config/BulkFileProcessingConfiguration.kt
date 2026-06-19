package com.sparjapati.bulkFileProcessing.config

import com.sparjapati.bulkFileProcessing.batch.BatchJobService
import com.sparjapati.bulkFileProcessing.batch.BulkJobCompletionHandlerRegistry
import com.sparjapati.bulkFileProcessing.batch.BulkTempFileCleanupRunner
import com.sparjapati.bulkFileProcessing.batch.FileProcessor
import com.sparjapati.bulkFileProcessing.batch.FileProcessingJobFactory
import com.sparjapati.bulkFileProcessing.batch.FileProcessorRegistry
import com.sparjapati.bulkFileProcessing.events.BulkJobCompletionHandler
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * Registers the bulk file processing beans. Imported via [@EnableBulkFileProcessing].
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
    ): FileProcessingJobFactory = FileProcessingJobFactory(
        jobRepository = jobRepository,
        transactionManager = transactionManager,
        handlerRegistry = handlerRegistry,
    )

    @Bean
    fun bulkTempFileCleanupRunner(): BulkTempFileCleanupRunner = BulkTempFileCleanupRunner()

    @Bean
    fun batchJobService(
        jobRepository: JobRepository,
        jobFactory: FileProcessingJobFactory,
        registry: FileProcessorRegistry,
    ): BatchJobService = BatchJobService(
        jobRepository = jobRepository,
        jobFactory = jobFactory,
        registry = registry,
    )
}
