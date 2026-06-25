package com.bulkFileProcessing.events

import com.bulkFileProcessing.batch.HasProcessorType

/**
 * Callback interface for reacting to a bulk file processing job's completion.
 *
 * Register any number of implementations as `@Component` beans — the library
 * discovers them at startup and routes each job's result to the handler whose
 * [processorType] matches. There is at most one handler per processor type.
 *
 * ```kotlin
 * @Component
 * class InvoiceJobCompletionHandler : BulkJobCompletionHandler {
 *     override val processorType = "invoice-upload"
 *     override fun onJobCompleted(result: BulkJobResult) { ... }
 * }
 * ```
 *
 * The handler does not have to be the same class as the
 * [FileProcessor][com.bulkFileProcessing.batch.FileProcessor] — it can be
 * any Spring bean that knows how to react to a specific processor type's completion.
 *
 * **Thread safety:** handler beans are singletons; [onJobCompleted] may be called
 * concurrently if multiple jobs of the same `processorType` run simultaneously.
 *
 * **Exceptions:** any [Exception] thrown from [onJobCompleted] propagates to the
 * calling thread after being logged — it does not affect temp file cleanup.
 */
interface BulkJobCompletionHandler : HasProcessorType {
    fun onJobCompleted(result: BulkJobResult)
}
