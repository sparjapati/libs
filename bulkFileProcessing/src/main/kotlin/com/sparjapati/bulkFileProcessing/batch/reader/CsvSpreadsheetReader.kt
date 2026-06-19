package com.sparjapati.bulkFileProcessing.batch.reader

import com.sparjapati.bulkFileProcessing.batch.SpreadsheetRow
import org.apache.commons.csv.CSVFormat
import org.springframework.batch.infrastructure.item.ItemReader
import java.io.FileReader

/**
 * [ItemReader] that reads data rows from a CSV file using Apache Commons CSV.
 *
 * The first row is treated as the header; subsequent rows are yielded as [SpreadsheetRow]
 * instances keyed by column name. Returns `null` when all rows have been consumed,
 * signalling Spring Batch that the step is complete.
 *
 * @param filePath absolute path to the CSV file on disk.
 */
class CsvSpreadsheetReader(filePath: String) : ItemReader<SpreadsheetRow> {

    private val parser = CSVFormat.DEFAULT
        .builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setTrim(true)
        .build()
        .parse(FileReader(filePath))

    private val iterator = parser.iterator()
    private var rowNumber = 0

    override fun read(): SpreadsheetRow? {
        if (!iterator.hasNext()) return null
        val record = iterator.next()
        return SpreadsheetRow(rowNumber = ++rowNumber, values = record.toMap())
    }
}
