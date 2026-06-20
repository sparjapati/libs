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
 *     override fun rowProcessor() = { results: Map<SpreadsheetRow, Invoice> ->
 *         val saved = repo.saveAll(results.values.toList()).associateBy { it.sourceRowId }
 *         results.keys.associateWith { row ->
 *             if (saved.containsKey(row.rowNumber)) RowResult.success(Unit)
 *             else RowResult.failure("save failed")
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
     * Return [RowResult.Success] for each row that was persisted, [RowResult.Failure] for
     * expected per-row errors (duplicate key, constraint violation, etc.) — the library
     * records failures in the result file without throwing.
     *
     * Unexpected system errors (DB unavailable, etc.) should still be thrown as exceptions
     * so Spring Batch's fault-tolerance and skip mechanism handles them.
     */
    fun rowProcessor(): (Map<SpreadsheetRow, T>) -> Map<SpreadsheetRow, RowResult<Unit>>
}
