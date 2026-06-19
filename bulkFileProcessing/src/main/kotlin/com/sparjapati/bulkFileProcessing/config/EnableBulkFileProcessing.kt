package com.sparjapati.bulkFileProcessing.config

import org.springframework.context.annotation.Import

/**
 * Add to a [@Configuration][org.springframework.context.annotation.Configuration] class
 * to enable bulk file processing. Registers [BatchJobService], [FileProcessorRegistry],
 * and [FileProcessingJobFactory] as Spring beans.
 *
 * Usage:
 * ```kotlin
 * @SpringBootApplication
 * @EnableBulkFileProcessing
 * class MyApplication
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(BulkFileProcessingConfiguration::class)
annotation class EnableBulkFileProcessing
