package com.sparjapati.bulkFileProcessing.batch.reader

import com.sparjapati.bulkFileProcessing.batch.RowAccumulator
import com.sparjapati.bulkFileProcessing.batch.SpreadsheetRow
import org.springframework.batch.infrastructure.item.ItemReader

/**
 * Auto-detecting [ItemReader] that delegates to the appropriate implementation
 * based on [fileType]:
 * - `"xlsx"` → [XlsxSpreadsheetReader] (Apache POI)
 * - anything else → [CsvSpreadsheetReader] (Apache Commons CSV)
 *
 * Each row returned by [read] is also registered with [accumulator] so it can be
 * included in the annotated result file written after the job completes.
 *
 * @param filePath    absolute path to the uploaded file on disk.
 * @param fileType    file extension, e.g. `"csv"` or `"xlsx"`.
 * @param accumulator per-job accumulator that tracks all rows and their errors.
 */
class SpreadsheetItemReader(
    filePath: String,
    fileType: String,
    private val accumulator: RowAccumulator,
) : ItemReader<SpreadsheetRow> {

    private val delegate: ItemReader<SpreadsheetRow> = when (fileType.lowercase()) {
        "xlsx" -> XlsxSpreadsheetReader(filePath = filePath)
        else -> CsvSpreadsheetReader(filePath = filePath)
    }

    override fun read(): SpreadsheetRow? {
        val row = delegate.read() ?: return null
        accumulator.recordRow(row)
        return row
    }
}
