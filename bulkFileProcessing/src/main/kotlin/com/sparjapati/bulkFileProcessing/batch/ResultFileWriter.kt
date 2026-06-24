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
 * Writes the annotated result file from data accumulated by [RowAccumulator].
 *
 * **Result file location:** `{resultBaseDir}/{processorType}/{date}/result-{originalFileName}.{ext}`
 *
 * **Single-pass optimisation (CSV, no errors, no extra columns):** the inline temp file written
 * by [RowAccumulator] is already the result — it is moved directly to the result path with no
 * second disk scan.
 *
 * **Extra columns:** column names are taken from [declaredExtraColumns] if set; otherwise
 * discovered from the first non-empty extras map recorded in [RowAccumulator].
 *
 * **XLSX output** always requires a second pass because [SXSSFWorkbook] is write-only.
 *
 * @param accumulator        the per-job accumulator holding rows, errors, and extras.
 * @param fileType           output file format: `"csv"` or `"xlsx"`.
 * @param processorType      forms the first level of the result directory.
 * @param originalFileName   original uploaded filename; used to name the result file.
 * @param resultBaseDir      root directory under which `{processorType}/{date}/` is created.
 * @param declaredExtraColumns column names and order declared by [FileProcessor.extraColumns];
 *   empty list means discover from the first non-empty extras map in [accumulator].
 */
class ResultFileWriter(
    private val accumulator: RowAccumulator,
    private val fileType: String,
    private val processorType: String,
    private val originalFileName: String,
    private val resultBaseDir: File,
    private val declaredExtraColumns: List<String> = emptyList(),
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ResultFileWriter::class.java)
        private const val STATUS_COLUMN = "status"
        private const val FAILURE_REASON_COLUMN = "failure-reason"
        private const val STATUS_SUCCESS = "SUCCESS"
        private const val STATUS_FAILED = "FAILED"
        private const val STATUS_SUCCESS_DESC = "-"
    }

    private val extraColumnNames: List<String>
        get() = declaredExtraColumns.ifEmpty { accumulator.discoveredExtraColumns }

    /**
     * Closes the [accumulator] and writes the annotated result file.
     *
     * - **CSV, no errors, no extras** — moves the inline file to the result location (1 pass).
     * - **CSV, errors or extras present** — re-reads inline, writes corrected output.
     * - **XLSX** — converts inline CSV → XLSX (always two passes; XLSX is write-only).
     *
     * @return absolute path of the result file, or `null` if no rows were read.
     */
    fun write(): String? {
        accumulator.close()

        if (accumulator.rowCount == 0) {
            LOGGER.info("No rows collected — skipping result file write")
            accumulator.inlineFile.deleteQuietly()
            return null
        }

        val outputFile = resolveResultFile()

        // Fast path: CSV with no errors and no extra columns — move inline file as-is.
        if (accumulator.errorMap.isEmpty() && accumulator.extrasMap.isEmpty() && fileType.lowercase() == "csv") {
            Files.move(accumulator.inlineFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            LOGGER.info("Result file written inline ({} rows, 0 errors): {}", accumulator.rowCount, outputFile.absolutePath)
            return outputFile.absolutePath
        }

        when (fileType.lowercase()) {
            "xlsx" -> writeXlsx(outputFile)
            else   -> writeCsv(outputFile)
        }

        accumulator.inlineFile.deleteQuietly()
        LOGGER.info(
            "Result file written ({} rows, {} errors, {} with extras): {}",
            accumulator.rowCount, accumulator.errorMap.size, accumulator.extrasMap.size, outputFile.absolutePath,
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
        val hdrs = accumulator.headers!!
        val extraCols = extraColumnNames
        val outputHeaders = hdrs + STATUS_COLUMN + FAILURE_REASON_COLUMN + extraCols

        CSVFormat.DEFAULT.builder()
            .setHeader(*outputHeaders.toTypedArray())
            .build()
            .print(outputFile.bufferedWriter())
            .use { printer ->
                accumulator.inlineFile.forEachDataRecord { i, record ->
                    val rowNumber = accumulator.rowNumbers[i]
                    val error = accumulator.errorMap[rowNumber]
                    val extras = accumulator.extrasMap[rowNumber] ?: emptyMap()
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
        val hdrs = accumulator.headers!!
        val extraCols = extraColumnNames
        val outputHeaders = hdrs + STATUS_COLUMN + FAILURE_REASON_COLUMN + extraCols

        SXSSFWorkbook(/* rowAccessWindowSize = */ 100).use { wb ->
            val sheet = wb.createSheet("result")
            val headerRow = sheet.createRow(0)
            outputHeaders.forEachIndexed { col, header -> headerRow.createCell(col).setCellValue(header) }

            var xlsxRowIdx = 1
            accumulator.inlineFile.forEachDataRecord { i, record ->
                val rowNumber = accumulator.rowNumbers[i]
                val error = accumulator.errorMap[rowNumber]
                val extras = accumulator.extrasMap[rowNumber] ?: emptyMap()
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
