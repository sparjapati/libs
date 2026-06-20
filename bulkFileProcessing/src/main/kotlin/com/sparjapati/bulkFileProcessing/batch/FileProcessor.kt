package com.sparjapati.bulkFileProcessing.batch

/**
 * Extension point for domain-specific bulk file processing.
 *
 * Implement this interface and annotate with [@Component][org.springframework.stereotype.Component]
 * to register a new processor type. [FileProcessorRegistry] discovers all implementations
 * automatically at startup and routes upload requests by [processorType].
 *
 * Both [rowReader] and [rowProcessor] operate on the full chunk at once, enabling bulk DB
 * operations (e.g. a single `findAllByIdIn` instead of N individual queries).
 * The [Map] return from [rowReader] is keyed by the original [SpreadsheetRow], so [rowProcessor]
 * always knows which row a domain object came from — no index-alignment required.
 *
 * Example:
 * ```kotlin
 * @Component
 * class InvoiceProcessor(private val repo: InvoiceRepository) : FileProcessor<Invoice> {
 *     override val processorType = "invoice_import"
 *
 *     override fun rowReader() = { rows: List<SpreadsheetRow> ->
 *         val ids = rows.map { it.values["vendor_id"]!! }
 *         val vendors = repo.findVendorsByIds(ids).associateBy { it.id }
 *         rows.associateWith { row ->
 *             val vendor = vendors[row.values["vendor_id"]]
 *             if (vendor == null) RowResult.failure("unknown vendor_id")
 *             else RowResult.success(Invoice.from(row, vendor))
 *         }
 *     }
 *
 *     // Declare column order explicitly (optional but recommended)
 *     override val extraColumns = listOf("invoice_id", "status_code")
 *
 *     override fun rowProcessor() = { results: Map<SpreadsheetRow, Invoice> ->
 *         val saved = repo.saveAll(results.values.toList())
 *         results.keys.zip(saved).associate { (row, invoice) ->
 *             row to RowResult.success(mapOf(
 *                 "invoice_id"  to invoice.id,
 *                 "status_code" to invoice.statusCode,
 *             ))
 *         }
 *     }
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

    /**
     * Returns a function that transforms a chunk of raw [SpreadsheetRow]s into domain objects.
     * Called once per chunk, enabling bulk DB reads or validations across the full chunk.
     *
     * Return [RowResult.Success] for each successfully mapped row, [RowResult.Failure] for
     * expected business errors (unknown reference, invalid format, etc.) — the library records
     * failures in the result file and excludes those rows from [rowProcessor].
     *
     * Unexpected system errors should still be thrown as exceptions so Spring Batch's
     * fault-tolerance handles retry and skip.
     */
    fun rowReader(): (List<SpreadsheetRow>) -> Map<SpreadsheetRow, RowResult<T>>

    /**
     * Returns a function that persists the transformed chunk.
     * Receives the [Map] of successfully transformed rows from [rowReader].
     *
     * Return [RowResult.Success] with an [ExtraColumns] map for each persisted row — the library
     * appends those key-value pairs as extra columns in the result file (e.g. `"account_id" to user.id`).
     * Return an empty map when no extra columns are needed for a row.
     *
     * Return [RowResult.Failure] for expected per-row errors (duplicate key, constraint violation,
     * etc.) — the library records the error in the result file without throwing.
     *
     * Unexpected system errors (DB unavailable, etc.) should still be thrown as exceptions
     * so Spring Batch's fault-tolerance and skip mechanism handles them.
     *
     * **Column order:** extra column headers are discovered from the first non-empty [ExtraColumns]
     * map returned. Use a consistent key order (e.g. `linkedMapOf`) across all rows, or declare
     * the order explicitly via [extraColumns].
     */
    fun rowProcessor(): (Map<SpreadsheetRow, T>) -> Map<SpreadsheetRow, RowResult<ExtraColumns>>

    /**
     * Declares the names and order of extra columns added by [rowProcessor].
     * Override this to guarantee column order regardless of which chunk is processed first.
     * Defaults to empty (order discovered from the first non-empty result).
     */
    val extraColumns: List<String> get() = emptyList()
}
