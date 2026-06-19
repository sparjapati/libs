package com.sparjapati.bulkFileProcessing.batch

import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemWriter

/**
 * Extension point for domain-specific bulk file processing.
 *
 * Implement this interface and annotate with [@Component][org.springframework.stereotype.Component]
 * to register a new processor type. [FileProcessorRegistry] discovers all implementations
 * automatically at startup and routes upload requests by [processorType].
 *
 * Example:
 * ```kotlin
 * @Component
 * class UserImportProcessor(private val userService: UserService) : FileProcessor<UserDto> {
 *     override val processorType = "user_import"
 *     override fun rowProcessor() = ItemProcessor { row -> userService.toDto(row) }
 *     override fun rowWriter() = ItemWriter { chunk -> userService.saveAll(chunk.items) }
 * }
 * ```
 *
 * @param T the domain object produced by [rowReader] and consumed by [rowProcessor].
 */
interface FileProcessor<T : Any> : HasProcessorType {

    /** Number of rows to read, process, and write per transaction chunk. Defaults to 100. */
    val chunkSize: Int get() = 100

    /**
     * Maximum number of rows that may be skipped due to errors before the job is aborted.
     * Defaults to [Long.MAX_VALUE] (unlimited — every bad row is skipped, job always completes).
     * Override with a lower value to fail fast on high error rates.
     */
    val skipLimit: Long get() = Long.MAX_VALUE

    /** Returns the [ItemProcessor] that transforms a raw [SpreadsheetRow] into [T]. */
    fun rowReader(): ItemProcessor<SpreadsheetRow, T>

    /** Returns the [ItemWriter] that persists a chunk of [T] objects. */
    fun rowProcessor(): ItemWriter<T>
}
