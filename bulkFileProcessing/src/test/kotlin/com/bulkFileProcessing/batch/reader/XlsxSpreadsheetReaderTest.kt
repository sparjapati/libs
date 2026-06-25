package com.bulkFileProcessing.batch.reader

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XlsxSpreadsheetReaderTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads all data rows with correct column mapping`() {
        val file = xlsxFile(
            headers = listOf("name", "email"),
            rows = listOf(
                listOf("Alice Smith", "alice@example.com"),
                listOf("Bob Jones", "bob@example.com"),
            ),
        )

        val reader = XlsxSpreadsheetReader(filePath = file.absolutePath)
        val rows = generateSequence { reader.read() }.toList()

        assertEquals(2, rows.size)
        assertEquals(1, rows[0].rowNumber)
        assertEquals("Alice Smith", rows[0].values["name"])
        assertEquals("alice@example.com", rows[0].values["email"])
        assertEquals(2, rows[1].rowNumber)
        assertEquals("Bob Jones", rows[1].values["name"])
        assertEquals("bob@example.com", rows[1].values["email"])
    }

    @Test
    fun `returns null immediately when file has header but no data rows`() {
        val file = xlsxFile(headers = listOf("name", "email"), rows = emptyList())
        val reader = XlsxSpreadsheetReader(filePath = file.absolutePath)
        assertNull(reader.read())
    }

    @Test
    fun `handles rows with missing cells by defaulting to empty string`() {
        val file = xlsxFile(
            headers = listOf("name", "email", "phone"),
            rows = listOf(listOf("Alice", "alice@example.com")), // phone cell absent
        )

        val reader = XlsxSpreadsheetReader(filePath = file.absolutePath)
        val row = reader.read()!!

        assertEquals("Alice", row.values["name"])
        assertEquals("alice@example.com", row.values["email"])
        assertEquals("", row.values["phone"])
        assertNull(reader.read())
    }

    @Test
    fun `assigns sequential row numbers starting at 1`() {
        val file = xlsxFile(
            headers = listOf("name"),
            rows = List(5) { i -> listOf("User $i") },
        )

        val reader = XlsxSpreadsheetReader(filePath = file.absolutePath)
        val rowNumbers = generateSequence { reader.read() }.map { it.rowNumber }.toList()

        assertEquals(listOf(1, 2, 3, 4, 5), rowNumbers)
    }

    @Test
    fun `returns null on repeated calls after all rows consumed`() {
        val file = xlsxFile(
            headers = listOf("name"),
            rows = listOf(listOf("Alice")),
        )

        val reader = XlsxSpreadsheetReader(filePath = file.absolutePath)
        reader.read() // consume the one data row
        assertNull(reader.read())
        assertNull(reader.read())
    }

    private fun xlsxFile(headers: List<String>, rows: List<List<String>>): File {
        val file = tempDir.resolve("test-${System.nanoTime()}.xlsx").toFile()
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet()
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
            rows.forEachIndexed { rowIdx, cols ->
                val row = sheet.createRow(rowIdx + 1)
                cols.forEachIndexed { colIdx, v -> row.createCell(colIdx).setCellValue(v) }
            }
            FileOutputStream(file).use { wb.write(it) }
        }
        return file
    }
}
