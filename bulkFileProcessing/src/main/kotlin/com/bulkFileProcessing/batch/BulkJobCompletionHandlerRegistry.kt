package com.bulkFileProcessing.batch

import com.bulkFileProcessing.events.BulkJobCompletionHandler
import org.slf4j.LoggerFactory

/**
 * Discovers all [BulkJobCompletionHandler] beans at startup and routes job completion
 * results to the correct handler by [BulkJobCompletionHandler.processorType].
 *
 * Duplicate [BulkJobCompletionHandler.processorType] values are rejected at startup
 * with an [IllegalArgumentException]. Having no handlers registered is valid — jobs
 * simply complete without a callback.
 *
 * Registered only when [@EnableBulkFileProcessing][com.bulkFileProcessing.config.EnableBulkFileProcessing]
 * is present on a configuration class.
 *
 * @param handlers all registered [BulkJobCompletionHandler] implementations.
 */
class BulkJobCompletionHandlerRegistry(handlers: List<BulkJobCompletionHandler>) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(BulkJobCompletionHandlerRegistry::class.java)
    }

    private val registry: Map<String, BulkJobCompletionHandler> = buildMap {
        handlers.forEach { handler ->
            val previous = put(handler.processorType, handler)
            if (previous != null) {
                throw IllegalArgumentException(
                    "Duplicate BulkJobCompletionHandler for processorType='${handler.processorType}': " +
                        "${previous::class.qualifiedName} and ${handler::class.qualifiedName}",
                )
            }
            LOGGER.info(
                "Registered BulkJobCompletionHandler '{}' → {}",
                handler.processorType,
                handler::class.simpleName,
            )
        }
    }

    /**
     * Returns the [BulkJobCompletionHandler] registered for [processorType], or `null`
     * if no handler is registered for that type.
     */
    fun find(processorType: String): BulkJobCompletionHandler? = registry[processorType]
}
