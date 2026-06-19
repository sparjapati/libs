package com.sparjapati.bulkFileProcessing.batch

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Discovers all [FileProcessor] beans at startup and routes upload requests to the
 * correct processor by [FileProcessor.processorType].
 *
 * Spring automatically injects every [FileProcessor] bean found in the application
 * context into the [processors] list. Duplicate [FileProcessor.processorType] values
 * are rejected at startup with an [IllegalArgumentException].
 *
 * @param processors all registered [FileProcessor] implementations.
 */
@Component
class FileProcessorRegistry(processors: List<FileProcessor<*>>) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FileProcessorRegistry::class.java)
    }

    private val registry: Map<String, FileProcessor<*>> = buildMap {
        processors.forEach { processor ->
            val previous = put(processor.processorType, processor)
            require(previous == null) {
                "Duplicate FileProcessor for processorType='${processor.processorType}': " +
                    "${previous!!::class.qualifiedName} and ${processor::class.qualifiedName}"
            }
            LOGGER.info(
                "Registered FileProcessor '{}' → {}",
                processor.processorType,
                processor::class.simpleName,
            )
        }
    }

    /**
     * Returns the [FileProcessor] registered for [processorType].
     *
     * @throws IllegalArgumentException if no processor is registered for the given type,
     *   with a message listing all known types to aid diagnosis.
     */
    fun get(processorType: String): FileProcessor<*> = registry[processorType]
        ?: throw IllegalArgumentException(
            "No FileProcessor registered for processorType='$processorType'. " +
                "Known types: ${registry.keys.sorted()}"
        )
}
