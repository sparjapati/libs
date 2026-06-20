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
        /** Convenience factory for a successful result. Used in [FileProcessor.rowReader]. */
        fun <T : Any> success(value: T): RowResult<T> = Success(value)

        /** Convenience factory for a failed result. */
        fun failure(error: String): RowResult<Nothing> = Failure(error)

        // ── rowProcessor helpers ──────────────────────────────────────────────────────────

        /**
         * Row was persisted successfully; no extra columns to append to the result file.
         *
         * ```kotlin
         * override fun rowProcessor() = { results: Map<SpreadsheetRow, User> ->
         *     userRepo.saveAll(results.values.toList())
         *     results.allSaved()
         * }
         * ```
         */
        fun noExtras(): RowResult<ExtraColumns> = Success(emptyMap())

        /**
         * Row was persisted successfully with extra columns specified inline.
         *
         * ```kotlin
         * row to RowResult.withExtras("account_id" to user.accountId, "status" to "ACTIVE")
         * ```
         */
        fun withExtras(vararg columns: Pair<String, String>): RowResult<ExtraColumns> =
            Success(mapOf(*columns))

        /**
         * Row was persisted successfully with extra columns from a pre-built map.
         *
         * ```kotlin
         * row to RowResult.withExtras(buildMap { put("account_id", user.accountId) })
         * ```
         */
        fun withExtras(columns: ExtraColumns): RowResult<ExtraColumns> = Success(columns)
    }
}

// ── Map<SpreadsheetRow, T> extensions for rowProcessor ───────────────────────────────────────────

/**
 * Maps every entry to [RowResult.noExtras] — use when all rows were persisted successfully
 * and there are no extra columns to append.
 *
 * ```kotlin
 * override fun rowProcessor() = { results: Map<SpreadsheetRow, Invoice> ->
 *     invoiceRepo.saveAll(results.values.toList())
 *     results.allSaved()
 * }
 * ```
 */
fun <T : Any> Map<SpreadsheetRow, T>.allSaved(): Map<SpreadsheetRow, RowResult<ExtraColumns>> =
    mapValues { RowResult.noExtras() }

/**
 * Maps each entry to a [RowResult] via [block], which receives the original [SpreadsheetRow]
 * and the transformed domain object. Use when the result or extra columns differ per row.
 *
 * ```kotlin
 * override fun rowProcessor() = { results: Map<SpreadsheetRow, User> ->
 *     val saved = userRepo.saveAll(results.values.toList()).associateBy { it.sourceId }
 *     results.toRowResults { row, user ->
 *         val created = saved[user.sourceId]
 *             ?: return@toRowResults RowResult.failure("save returned no entity")
 *         RowResult.withExtras("account_id" to created.accountId, "status" to created.status)
 *     }
 * }
 * ```
 */
fun <T : Any> Map<SpreadsheetRow, T>.toRowResults(
    block: (row: SpreadsheetRow, value: T) -> RowResult<ExtraColumns>,
): Map<SpreadsheetRow, RowResult<ExtraColumns>> =
    entries.associate { (row, value) -> row to block(row, value) }
