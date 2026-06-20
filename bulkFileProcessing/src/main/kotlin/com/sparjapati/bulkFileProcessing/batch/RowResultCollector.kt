package com.sparjapati.bulkFileProcessing.batch

import org.apache.commons.csv.CSVFormat
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

/**
 * Collects every row read during a batch job, any per-row processing errors, and any extra
 * columns contributed by [FileProcessor.rowProcessor], then writes an annotated result file.
 *
 * **Result file location:** `{resultBaseDir}/{processorType}/{date}/result-{originalFileName}.{ext}`
 *
 * **Single-pass optimisation (CSV, no errors, no extra columns):** rows are written directly to
 * an inline temp CSV during reading, pre-stamped as SUCCESS. If no errors or extra columns occur,
 * that file is moved to the structured result path — no second disk scan.
 *
 * **Extra columns:** [FileProcessor.rowProcessor] can return per-row key-value pairs (e.g.
 * `"account_id" to "ACC-123"`) via [RowResult.Success]. These are collected in [rowExtras]
 * and appended as additional columns after `failure-reason` in the result file.
 * Column names are taken from [FileProcessor.extraColumns] if declared, otherwise discovered
 * from the first non-empty extras map returned during the job.
 *
 * **XLSX output** always requires a second pass because [SXSSFWorkbook] is write-only.
 *
 * **Memory:** error messages and extra column values are held in memory (one map entry per row).
 * Row content itself is always on disk.
 *
 * @param fileType         output file format: `"csv"` or `"xlsx"`.
 * @param processorType    forms the first level of the result directory.
 * @param originalFileName original uploaded filename; used to name the result file.
 * @param resultBaseDir    root directory under which `{processorType}/{date}/` is created.
 * @param declaredExtraColumns column names and order declared by [FileProcessor.extraColumns];
 *   empty list means discover from first non-empty extras map.
 */
class RowResultCollector(
    private val fileType: String,
    private val processorType: String,
    private val originalFileName: String,
    private val resultBaseDir: File,
    private val declaredExtraColumns: List<String> = emptyList(),
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RowResultCollector::class.java)
        private const val STATUS_COLUMN = "status"
        private const val FAILURE_REASON_COLUMN = "failure-reason"
        private const val STATUS_SUCCESS = "SUCCESS"
        private const val STATUS_FAILED = "FAILED"
        private const val STATUS_SUCCESS_DESC = "-"
    }

    private val errors = HashMap<Int, String>()        // rowNumber → error message
    private val rowExtras = HashMap<Int, ExtraColumns>() // rowNumber → extra column values
    private var discoveredExtraColumns: List<String> = emptyList()

    private var rowCount = 0
    private var headers: List<String>? = null

    // Ordered row numbers — position i in this list corresponds to record i in the inline file.
    private val rowNumbers = mutableListOf<Int>()

    // Inline file: written during reading with all rows pre-stamped SUCCESS.
    private val inlineFile = Files.createTempFile(BulkTempFileCleanupRunner.PREFIX_INLINE, ".csv").toFile()
    private val inlinePrinter = CSVFormat.DEFAULT.print(inlineFile.bufferedWriter())

    /** The active extra column names: declared list if set, otherwise discovered at runtime. */
    private val extraColumnNames: List<String>
        get() = declaredExtraColumns.ifEmpty { discoveredExtraColumns }

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
     */
    fun recordError(rowNumber: Int, error: String) {
        errors[rowNumber] = error
    }

    /**
     * Records extra columns for row [rowNumber] returned by [FileProcessor.rowProcessor].
     * Ignored if [extra] is empty. On the first non-empty call, discovers column names
     * from the map's keys (preserving insertion order) unless already declared via
     * [FileProcessor.extraColumns].
     */
    fun recordExtra(rowNumber: Int, extra: ExtraColumns) {
        if (extra.isEmpty()) return
        rowExtras[rowNumber] = extra
        if (discoveredExtraColumns.isEmpty()) {
            discoveredExtraColumns = extra.keys.toList()
        }
    }

    /**
     * Returns the path to the annotated result file.
     *
     * - **CSV, no errors, no extras** — moves the inline file to the result location (1 pass).
     * - **CSV, errors or extras present** — re-reads inline, writes corrected output.
     * - **XLSX** — converts inline CSV → XLSX (always two passes; XLSX is write-only).
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

        val outputFile = resolveResultFile()

        // Fast path: CSV with no errors and no extra columns — move inline file as-is
        if (errors.isEmpty() && rowExtras.isEmpty() && fileType.lowercase() == "csv") {
            Files.move(inlineFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            LOGGER.info("Result file written inline ({} rows, 0 errors): {}", rowCount, outputFile.absolutePath)
            return outputFile.absolutePath
        }

        when (fileType.lowercase()) {
            "xlsx" -> writeXlsx(outputFile)
            else   -> writeCsv(outputFile)
        }

        inlineFile.deleteQuietly()
        LOGGER.info(
            "Result file written ({} rows, {} errors, {} with extras): {}",
            rowCount, errors.size, rowExtras.size, outputFile.absolutePath,
        )
        return outputFile.absolutePath
    }

    private fun resolveResultFile(): File {
        val date = LocalDate.now().toString()
        val sanitizedBase = originalFileName
            .substringBeforeLast('.')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100)
        val dir = File(resultBaseDir, "$processorType/$date")
        dir.mkdirs()
        return File(dir, "result-$sanitizedBase.$fileType")
    }

    private fun writeCsv(outputFile: File) {
        val hdrs = headers!!
        val extraCols = extraColumnNames
        val outputHeaders = hdrs + STATUS_COLUMN + FAILURE_REASON_COLUMN + extraCols

        CSVFormat.DEFAULT.builder()
            .setHeader(*outputHeaders.toTypedArray())
            .build()
            .print(outputFile.bufferedWriter())
            .use { printer ->
                inlineFile.forEachDataRecord { i, record ->
                    val rowNumber = rowNumbers[i]
                    val error = errors[rowNumber]
                    val extras = rowExtras[rowNumber] ?: emptyMap()
                    printer.printRecord(buildList {
                        repeat(hdrs.size) { col -> add(record[col]) }
                        add(if (error == null) STATUS_SUCCESS else STATUS_FAILED)
                        add(error ?: "")
                        extraCols.forEach { add(extras[it] ?: "") }
                    })
                }
            }
    }

    private fun writeXlsx(outputFile: File) {
        val hdrs = headers!!
        val extraCols = extraColumnNames
        val outputHeaders = hdrs + STATUS_COLUMN + FAILURE_REASON_COLUMN + extraCols

        SXSSFWorkbook(/* rowAccessWindowSize = */ 100).use { wb ->
            val sheet = wb.createSheet("result")
            val headerRow = sheet.createRow(0)
            outputHeaders.forEachIndexed { col, header -> headerRow.createCell(col).setCellValue(header) }

            var xlsxRowIdx = 1
            inlineFile.forEachDataRecord { i, record ->
                val rowNumber = rowNumbers[i]
                val error = errors[rowNumber]
                val extras = rowExtras[rowNumber] ?: emptyMap()
                val xlsxRow = sheet.createRow(xlsxRowIdx++)
                repeat(hdrs.size) { col -> xlsxRow.createCell(col).setCellValue(record[col]) }
                xlsxRow.createCell(hdrs.size).setCellValue(if (error == null) STATUS_SUCCESS else STATUS_FAILED)
                xlsxRow.createCell(hdrs.size + 1).setCellValue(error ?: STATUS_SUCCESS_DESC)
                extraCols.forEachIndexed { i2, key ->
                    xlsxRow.createCell(hdrs.size + 2 + i2).setCellValue(extras[key] ?: "")
                }
            }

            FileOutputStream(outputFile).use { wb.write(it) }
        }
    }

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
