package com.sparjapati.bulkFileProcessing.batch

/**
 * Represents a single data row read from a CSV or XLSX file.
 *
 * @param rowNumber 1-based row index in the source file, excluding the header row.
 * @param values    Map of column name → cell value, derived from the header row.
 */
data class SpreadsheetRow(
    val rowNumber: Int,
    val values: Map<String, String>,
)
