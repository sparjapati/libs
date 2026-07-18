package com.bulkFileProcessing.batch

import org.slf4j.LoggerFactory

/**
 * Discovers all [FileProcessor] beans at startup and routes upload requests to the
 * correct processor by [FileProcessor.processorType].
 *
 * Spring automatically injects every [FileProcessor] bean found in the application
 * context into the [processors] list. Duplicate [FileProcessor.processorType] values
 * are rejected at startup with an [IllegalArgumentException].
 *
 * Registered only when [@EnableBulkFileProcessing][com.bulkFileProcessing.config.EnableBulkFileProcessing]
 * is present on a configuration class.
 *
 * @param processors all registered [FileProcessor] implementations.
 */
class FileProcessorRegistry(processors: List<FileProcessor<*>>) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FileProcessorRegistry::class.java)
    }

    private val registry: Map<ProcessorType, FileProcessor<*>> = buildMap {
        processors.forEach { processor ->
            val previous = put(processor.processorType, processor)
            if (previous != null) {
                throw IllegalArgumentException(
                    "Duplicate FileProcessor for processorType='${processor.processorType}': " +
                        "${previous::class.qualifiedName} and ${processor::class.qualifiedName}",
                )
            }
            LOGGER.info(
                "Registered FileProcessor '{}' → {}",
                processor.processorType,
                processor::class.simpleName,
            )
        }
    }

    /**
     * Returns the [FileProcessor] registered for [processorType], or `null` if none is registered.
     * The caller is responsible for handling the `null` case.
     */
    fun find(processorType: ProcessorType): FileProcessor<*>? = registry[processorType]
}
