package com.sparjapati.bulkFileProcessing.batch

/**
 * Extra columns appended to a result row by [FileProcessor.rowProcessor].
 * Keys become column headers; values are written into the corresponding cells.
 * An empty map means no extra columns for that row.
 */
typealias ExtraColumns = Map<String, String>

/**
 * Represents the outcome of transforming a single [SpreadsheetRow] in [FileProcessor.rowReader].
 *
 * Return [Success] when the row maps cleanly to a domain object.
 * Return [Failure] for expected business errors (missing field, invalid format, etc.) —
 * the library records the [Failure.error] in the result file without throwing.
 *
 * Unexpected system errors (DB unavailable, NPE, etc.) should still be thrown as exceptions
 * so Spring Batch's fault-tolerance and skip mechanism handles them.
 */
sealed class RowResult<out T : Any> {

    /** Row transformed successfully; [value] is passed to [FileProcessor.rowProcessor]. */
    data class Success<out T : Any>(val value: T) : RowResult<T>()

    /** Row failed with a human-readable [error] reason written to the result file. */
    data class Failure(val error: String) : RowResult<Nothing>()

    companion object {
        /** Convenience factory for a successful result. */
        fun <T : Any> success(value: T): RowResult<T> = Success(value)

        /** Convenience factory for a failed result. */
        fun failure(error: String): RowResult<Nothing> = Failure(error)
    }
}
