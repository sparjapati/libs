package com.sparjapati.bulkFileProcessing.batch

import org.apache.commons.csv.CSVFormat
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/**
 * Collects every row read during a batch job and any per-row processing errors,
 * then writes an annotated result file where failed rows carry extra `status` and
 * `failure-reason` columns.
 *
 * **Single-pass optimisation (CSV, no errors):** rows are written directly to the
 * result file during reading, pre-stamped as SUCCESS. If no errors occur, that file
 * is returned as-is — no second disk scan. Only when errors are present is a
 * corrective second pass performed to rewrite the affected rows.
 *
 * **XLSX output** always requires a second pass because [SXSSFWorkbook] is write-only
 * (flushed rows cannot be read back). The inline CSV is used as the intermediate and
 * converted to XLSX in [writeResultFile].
 *
 * **Memory:** only error messages are held in memory (`rowNumber → error`). Row numbers
 * are kept as an ordered [Int] list (~4 bytes each) to map record position → row number
 * during the corrective pass without embedding row numbers in the inline file.
 *
 * One instance is created per job run and shared between the item reader and
 * [RowSkipListener]. No synchronisation is needed — Spring Batch steps are single-threaded.
 *
 * @param fileType output file format: `"csv"` (default) or `"xlsx"`.
 */
class RowResultCollector(private val fileType: String) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RowResultCollector::class.java)
        private const val STATUS_COLUMN = "status"
        private const val FAILURE_REASON_COLUMN = "failure-reason"
        private const val STATUS_SUCCESS = "SUCCESS"
        private const val STATUS_FAILED = "FAILED"
        private const val STATUS_SUCCESS_DESC = "-"
    }

    private val errors = HashMap<Int, String>()   // rowNumber → error message
    private var rowCount = 0
    private var headers: List<String>? = null

    // Ordered row numbers — position i in this list corresponds to record i in the inline file.
    // Avoids embedding row numbers as a synthetic column in the inline file.
    private val rowNumbers = mutableListOf<Int>()

    // Inline file: written during reading with all rows pre-stamped SUCCESS.
    // For CSV with no errors this IS the final result — no second pass needed.
    private val inlineFile = Files.createTempFile(BulkTempFileCleanupRunner.PREFIX_INLINE, ".csv").toFile()
    private val inlinePrinter = CSVFormat.DEFAULT.print(inlineFile.bufferedWriter())

    /**
     * Streams [row] to the inline file. Writes the header row on the first call.
     * Pre-stamps each row as SUCCESS; errors are corrected in [writeResultFile] if needed.
     */
    fun recordRow(row: SpreadsheetRow) {
        if (headers == null) {
            headers = row.values.keys.toList()
            inlinePrinter.printRecord(headers!! + STATUS_COLUMN + FAILURE_REASON_COLUMN)
        }
        rowNumbers.add(row.rowNumber)
        inlinePrinter.printRecord(buildList {
            headers!!.forEach { add(row.values[it] ?: "") }
            add(STATUS_SUCCESS)
            add("")
        })
        rowCount++
    }

    /**
     * Marks row [rowNumber] as failed with [error].
     * No-op if the row was never recorded (e.g. read-phase failure before [recordRow]).
     */
    fun recordError(rowNumber: Int, error: String) {
        errors[rowNumber] = error
    }

    /**
     * Returns the path to the annotated result file.
     *
     * - **CSV, no errors** — returns the inline file directly (single pass total).
     * - **CSV, errors present** — rewrites the inline file with corrected status/reason.
     * - **XLSX** — converts the inline CSV to XLSX (always two passes; XLSX is write-only).
     *
     * @return absolute path of the result file, or `null` if no rows were read.
     */
    fun writeResultFile(): String? {
        inlinePrinter.flush()
        inlinePrinter.close()

        if (rowCount == 0) {
            LOGGER.info("No rows collected — skipping result file write")
            inlineFile.deleteQuietly()
            return null
        }

        // Fast path: CSV with no errors — inline file is already the final result
        if (errors.isEmpty() && fileType.lowercase() == "csv") {
            LOGGER.info("Result file written inline ({} rows, 0 errors): {}", rowCount, inlineFile.absolutePath)
            return inlineFile.absolutePath
        }

        val outputFile = Files.createTempFile(BulkTempFileCleanupRunner.PREFIX_RESULT, ".$fileType").toFile()
        when (fileType.lowercase()) {
            "xlsx" -> writeXlsx(outputFile)
            else   -> writeCsv(outputFile)
        }

        inlineFile.deleteQuietly()
        LOGGER.info("Result file written ({} rows, {} errors): {}", rowCount, errors.size, outputFile.absolutePath)
        return outputFile.absolutePath
    }

    // Corrective pass for CSV — re-reads inline file and fixes error rows.
    private fun writeCsv(outputFile: File) {
        val hdrs = headers!!
        val outputHeaders = hdrs + STATUS_COLUMN + FAILURE_REASON_COLUMN

        CSVFormat.DEFAULT.builder()
            .setHeader(*outputHeaders.toTypedArray())
            .build()
            .print(outputFile.bufferedWriter())
            .use { printer ->
                inlineFile.forEachDataRecord { i, record ->
                    val error = errors[rowNumbers[i]]
                    printer.printRecord(buildList {
                        repeat(hdrs.size) { col -> add(record[col]) }
                        add(if (error == null) STATUS_SUCCESS else STATUS_FAILED)
                        add(error ?: "")
                    })
                }
            }
    }

    // Converts inline CSV to XLSX — always a second pass (SXSSFWorkbook is write-only).
    private fun writeXlsx(outputFile: File) {
        val hdrs = headers!!
        val outputHeaders = hdrs + STATUS_COLUMN + FAILURE_REASON_COLUMN

        SXSSFWorkbook(/* rowAccessWindowSize = */ 100).use { wb ->
            val sheet = wb.createSheet("result")
            val headerRow = sheet.createRow(0)
            outputHeaders.forEachIndexed { col, header -> headerRow.createCell(col).setCellValue(header) }

            var xlsxRowIdx = 1
            inlineFile.forEachDataRecord { i, record ->
                val error = errors[rowNumbers[i]]
                val xlsxRow = sheet.createRow(xlsxRowIdx++)
                repeat(hdrs.size) { col -> xlsxRow.createCell(col).setCellValue(record[col]) }
                xlsxRow.createCell(hdrs.size).setCellValue(if (error == null) STATUS_SUCCESS else STATUS_FAILED)
                xlsxRow.createCell(hdrs.size + 1).setCellValue(error ?: STATUS_SUCCESS_DESC)
            }

            FileOutputStream(outputFile).use { wb.write(it) }
        }
    }

    // Parses the inline CSV, skips the header row, and calls [block] for each data record
    // with its 0-based index (aligned with [rowNumbers]).
    private fun File.forEachDataRecord(block: (index: Int, record: org.apache.commons.csv.CSVRecord) -> Unit) {
        CSVFormat.DEFAULT.parse(bufferedReader()).use { records ->
            val iterator = records.iterator()
            if (iterator.hasNext()) iterator.next() // skip header
            var i = 0
            while (iterator.hasNext()) block(i++, iterator.next())
        }
    }

    private fun File.deleteQuietly() {
        if (!delete()) LOGGER.warn("Failed to delete temp file '{}'", absolutePath)
    }
}
