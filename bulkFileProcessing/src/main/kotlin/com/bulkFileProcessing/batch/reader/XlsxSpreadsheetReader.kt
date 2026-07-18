package com.bulkFileProcessing.batch.reader

import com.bulkFileProcessing.batch.SpreadsheetRow
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler
import org.apache.poi.xssf.usermodel.XSSFComment
import org.apache.poi.util.XMLHelper
import org.springframework.batch.infrastructure.item.ItemReader
import org.xml.sax.InputSource
import java.io.File

/**
 * Memory-efficient [ItemReader] for XLSX files using Apache POI's SAX event model.
 *
 * The DOM-based [org.apache.poi.xssf.usermodel.XSSFWorkbook] parses the entire file into an
 * in-memory object tree before yielding any rows — typically 5–10× the raw file size in heap.
 * This reader uses [XSSFReader] + [XSSFSheetXMLHandler] to walk the sheet XML incrementally,
 * building only lightweight [SpreadsheetRow] instances (plain `Map<String, String>`). Per-row
 * overhead is an order of magnitude lower than POI's cell / style / formula objects.
 *
 * The first row of the first sheet is treated as the header; subsequent rows are returned by
 * [read] in order. Cells absent from a row default to an empty string.
 *
 * @param filePath absolute path to the XLSX file on disk.
 */
class XlsxSpreadsheetReader(filePath: String) : ItemReader<SpreadsheetRow> {

    private val rowIterator: Iterator<SpreadsheetRow> = parseRows(filePath).iterator()

    override fun read(): SpreadsheetRow? = if (rowIterator.hasNext()) rowIterator.next() else null

    private fun parseRows(filePath: String): List<SpreadsheetRow> = buildList {
        OPCPackage.open(File(filePath)).use { pkg ->
            val xssfReader = XSSFReader(pkg)
            val sharedStrings = ReadOnlySharedStringsTable(pkg)
            val styles = xssfReader.stylesTable
            val sheetsData = xssfReader.sheetsData

            if (!sheetsData.hasNext()) return@buildList

            sheetsData.next().use { sheetStream ->
                val handler = RowCollectingHandler(result = this)
                XMLHelper.newXMLReader().also { xmlReader ->
                    xmlReader.contentHandler = XSSFSheetXMLHandler(
                        styles, null, sharedStrings, handler, DataFormatter(), false,
                    )
                    xmlReader.parse(InputSource(sheetStream))
                }
            }
        }
    }

    /**
     * SAX [SheetContentsHandler] that accumulates [SpreadsheetRow] instances into [result].
     *
     * Treats the first row as the header. Uses a [sortedMapOf] keyed by column index so
     * sparse rows (missing cells) are handled correctly — absent indices default to `""`.
     */
    private class RowCollectingHandler(
        private val result: MutableList<SpreadsheetRow>,
    ) : SheetContentsHandler {

        private val headers = mutableListOf<String>()
        private var isHeaderRow = true
        private var rowNumber = 0
        private val rowData = sortedMapOf<Int, String>()

        override fun startRow(rowNum: Int) = rowData.clear()

        override fun endRow(rowNum: Int) {
            if (isHeaderRow) {
                headers.addAll(rowData.values)
                isHeaderRow = false
            } else {
                result.add(
                    SpreadsheetRow(
                        rowNumber = ++rowNumber,
                        values = buildMap {
                            headers.forEachIndexed { i, h -> put(h, rowData[i] ?: "") }
                        },
                    )
                )
            }
        }

        override fun cell(ref: String, value: String?, comment: XSSFComment?) {
            rowData[CellReference(ref).col.toInt()] = value ?: ""
        }

        override fun headerFooter(text: String, isHeader: Boolean, tagName: String) {}
    }
}
