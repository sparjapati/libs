package com.sparjapati.bulkFileProcessing.batch

import org.apache.commons.csv.CSVFormat
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.nio.file.Files

/**
 * Collects every row read during a batch job and any per-row processing errors,
 * then writes an annotated result file where failed rows carry extra `status` and
 * `failure-reason` columns.
 *
 * **Large-file design**: rows are streamed to a temp CSV on disk as they arrive via
 * [recordRow] — the full dataset is never held in memory. Only error messages are
 * kept in memory (a [HashMap] of rowNumber → error), which is a small fraction of
 * total rows for typical uploads. The result file is produced with a single streaming
 * pass over the temp file.
 *
 * For xlsx output [SXSSFWorkbook] is used so the workbook is also written in a
 * streaming fashion (default window: 100 rows in memory at a time).
 *
 * One instance is created per job run and shared between the item reader
 * (which calls [recordRow] for each row it reads) and [RowSkipListener] (which
 * calls [recordError] for each row that fails processing or writing). Because
 * Spring Batch runs a single-threaded step by default, no synchronization is needed.
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

        /** Index of the synthetic row-number column prepended to every temp-file record. */
        private const val ROW_NUMBER_COL = 0
    }

    // Only errors are kept in memory — a small fraction of total rows
    private val errors = HashMap<Int, String>() // rowNumber → error message
    private var rowCount = 0
    private var headers: List<String>? = null

    // Rows are streamed to disk immediately; the first column is the row number
    // so it can be looked up during result-file annotation without re-parsing
    // the source file.
    private val rowTempFile = Files.createTempFile("bulk-rows-", ".csv").toFile()
    private val rowPrinter = CSVFormat.DEFAULT.print(rowTempFile.bufferedWriter())

    /** Streams [row] to the temp file. Captures column headers on the first call. */
    fun recordRow(row: SpreadsheetRow) {
        if (headers == null) {
            headers = row.values.keys.toList()
        }
        rowPrinter.printRecord(buildList {
            add(row.rowNumber)
            headers!!.forEach { add(row.values[it] ?: "") }
        })
        rowCount++
    }

    /**
     * Marks a previously recorded row as failed with [error].
     * If no row with [rowNumber] was recorded (e.g. a read-phase failure), the call
     * is a no-op — read errors are captured separately in the batch skip count.
     */
    fun recordError(rowNumber: Int, error: String) {
        errors[rowNumber] = error
    }

    /**
     * Writes the annotated result file and returns its absolute path.
     *
     * The file contains all rows that were read, with two extra columns appended:
     * - `status` — `"SUCCESS"` or `"FAILED"`
     * - `failure-reason` — the exception message for skipped rows; `"-"` for successful rows.
     *
     * Rows are streamed from the temp file — the full dataset is never held in memory.
     *
     * @return absolute path of the written result file, or `null` if no rows were read.
     */
    fun writeResultFile(): String? {
        rowPrinter.flush()
        rowPrinter.close()

        if (rowCount == 0) {
            LOGGER.info("No rows collected — skipping result file write")
            if (!rowTempFile.delete()) LOGGER.warn("Failed to delete temp row file '{}'", rowTempFile.absolutePath)
            return null
        }

        val hdrs = headers!! // safe: rowCount > 0 implies at least one recordRow call
        val outputHeaders = hdrs + STATUS_COLUMN + FAILURE_REASON_COLUMN
        val outputFile = Files.createTempFile("bulk-result-", ".$fileType").toFile()

        when (fileType.lowercase()) {
            "xlsx" -> writeXlsx(outputFile = outputFile, dataHeaders = hdrs, outputHeaders = outputHeaders)
            else -> writeCsv(outputFile = outputFile, outputHeaders = outputHeaders, dataColumnCount = hdrs.size)
        }

        if (!rowTempFile.delete()) LOGGER.warn("Failed to delete temp row file '{}'", rowTempFile.absolutePath)
        LOGGER.info("Result file written: {} ({} rows)", outputFile.absolutePath, rowCount)
        return outputFile.absolutePath
    }

    private fun writeCsv(outputFile: java.io.File, outputHeaders: List<String>, dataColumnCount: Int) {
        CSVFormat.DEFAULT
            .builder()
            .setHeader(*outputHeaders.toTypedArray())
            .build()
            .print(outputFile.bufferedWriter())
            .use { printer ->
                CSVFormat.DEFAULT.parse(rowTempFile.bufferedReader()).use { records ->
                    for (record in records) {
                        val rowNumber = record[ROW_NUMBER_COL].toInt()
                        val error = errors[rowNumber]
                        printer.printRecord(buildList {
                            for (col in 1..dataColumnCount) add(record[col])
                            add(if (error == null) STATUS_SUCCESS else STATUS_FAILED)
                            add(error ?: "")
                        })
                    }
                }
            }
    }

    private fun writeXlsx(outputFile: java.io.File, dataHeaders: List<String>, outputHeaders: List<String>) {
        // SXSSFWorkbook keeps only `rowAccessWindowSize` rows in memory at a time,
        // flushing older rows to a temp file — safe for arbitrarily large outputs.
        SXSSFWorkbook(/* rowAccessWindowSize = */ 100).use { wb ->
            val sheet = wb.createSheet("result")

            val headerRow = sheet.createRow(0)
            outputHeaders.forEachIndexed { col, header -> headerRow.createCell(col).setCellValue(header) }

            CSVFormat.DEFAULT.parse(rowTempFile.bufferedReader()).use { records ->
                var xlsxRowIdx = 1
                for (record in records) {
                    val rowNumber = record[ROW_NUMBER_COL].toInt()
                    val error = errors[rowNumber]
                    val xlsxRow = sheet.createRow(xlsxRowIdx++)
                    for (col in 1..dataHeaders.size) {
                        xlsxRow.createCell(col - 1).setCellValue(record[col])
                    }
                    xlsxRow.createCell(dataHeaders.size).setCellValue(if (error == null) STATUS_SUCCESS else STATUS_FAILED)
                    xlsxRow.createCell(dataHeaders.size + 1).setCellValue(error ?: STATUS_SUCCESS_DESC)
                }
            }

            FileOutputStream(outputFile).use { wb.write(it) }
        }
    }
}
