package com.sparjapati.bulkFileProcessing.events

/**
 * Optional callback interface for [com.sparjapati.bulkFileProcessing.batch.FileProcessor]
 * implementations that need to react to their own job's completion.
 *
 * Implement this alongside [com.sparjapati.bulkFileProcessing.batch.FileProcessor] to receive
 * a [BulkJobResult] directly after your processor's job finishes:
 *
 * ```kotlin
 * @Component
 * class InvoiceUploadProcessor : FileProcessor<Invoice>, BulkJobCompletionHandler {
 *     override val processorType = "invoice-upload"
 *     override fun onJobCompleted(result: BulkJobResult) { ... }
 * }
 * ```
 *
 * **Thread safety:** the processor bean is a singleton; [onJobCompleted] may be called
 * concurrently if multiple jobs of the same `processorType` run simultaneously.
 *
 * **Exceptions:** any [Exception] thrown is caught and logged — it does not affect
 * temp file cleanup or job status recording.
 */
interface BulkJobCompletionHandler {
    fun onJobCompleted(result: BulkJobResult)
}
