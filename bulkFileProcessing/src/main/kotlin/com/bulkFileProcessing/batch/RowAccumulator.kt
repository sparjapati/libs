package com.bulkFileProcessing.batch

import org.apache.commons.csv.CSVFormat
import java.io.File
import java.nio.file.Files

/**
 * Accumulates every row, per-row error, and per-row extra columns produced during a batch job.
 *
 * Streams each row to an inline temp CSV on disk as it is read (pre-stamped SUCCESS), keeping
 * only error messages and extra column values in memory. The accumulated state is consumed by
 * [ResultFileWriter] after the job finishes to produce the annotated result file.
 *
 * Call [close] before handing the accumulator to [ResultFileWriter] — it flushes and closes the
 * inline file printer. [ResultFileWriter.write] does this automatically.
 */
class RowAccumulator {

    private val errors = HashMap<Int, String>()
    private val rowExtras = HashMap<Int, ExtraColumns>()
    private val _rowNumbers = mutableListOf<Int>()

    // Inline file: every row written here during reading, pre-stamped SUCCESS.
    internal val inlineFile: File = Files.createTempFile(BulkTempFileCleanupRunner.PREFIX_INLINE, ".csv").toFile()
    private val inlinePrinter = CSVFormat.DEFAULT.print(inlineFile.bufferedWriter())

    var headers: List<String>? = null
        private set

    var rowCount: Int = 0
        private set

    var discoveredExtraColumns: List<String> = emptyList()
        private set

    val rowNumbers: List<Int> get() = _rowNumbers
    val errorMap: Map<Int, String> get() = errors
    val extrasMap: Map<Int, ExtraColumns> get() = rowExtras

    /**
     * Streams [row] to the inline file. Writes the header row on the first call.
     * Pre-stamps each row as SUCCESS; errors are corrected by [ResultFileWriter] if needed.
     */
    fun recordRow(row: SpreadsheetRow) {
        val isFirstRow = headers == null
        if (isFirstRow) {
            headers = row.values.keys.toList()
        }
        val hdrs = checkNotNull(headers) { "RowAccumulator.recordRow: headers is null after initialization" }
        if (isFirstRow) {
            inlinePrinter.printRecord(hdrs + STATUS_COLUMN + FAILURE_REASON_COLUMN)
        }
        _rowNumbers.add(row.rowNumber)
        inlinePrinter.printRecord(buildList {
            hdrs.forEach { add(row.values[it].takeUnless { v -> v.isNullOrBlank() } ?: EMPTY_CELL_VALUE) }
            add(STATUS_SUCCESS)
            add(EMPTY_CELL_VALUE)
        })
        rowCount++
    }

    /** Marks row [rowNumber] as failed with [error]. */
    fun recordError(rowNumber: Int, error: String) {
        errors[rowNumber] = error
    }

    /**
     * Records extra columns for row [rowNumber] returned by [FileProcessor.rowProcessor].
     * Ignored if [extra] is empty. On the first non-empty call, discovers column names from
     * the map's keys unless a later [ResultFileWriter] has declared them explicitly.
     */
    fun recordExtra(rowNumber: Int, extra: ExtraColumns) {
        if (extra.isEmpty()) return
        rowExtras[rowNumber] = extra
        if (discoveredExtraColumns.isEmpty()) {
            discoveredExtraColumns = extra.keys.toList()
        }
    }

    /** Flushes and closes the inline file printer. Called by [ResultFileWriter.write]. */
    internal fun close() {
        inlinePrinter.flush()
        inlinePrinter.close()
    }

}
