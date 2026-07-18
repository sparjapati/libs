package com.bulkFileProcessing.batch

/** Identifier for a registered [FileProcessor]/[com.bulkFileProcessing.events.BulkJobCompletionHandler] type. */
typealias ProcessorType = String

/**
 * Marker interface for components that are scoped to a specific bulk file processor type.
 *
 * Implemented by [FileProcessor] and
 * [com.bulkFileProcessing.events.BulkJobCompletionHandler] so that both
 * registries share the same routing key contract.
 */
interface HasProcessorType {
    /** Unique identifier for the processor type this component handles. */
    val processorType: ProcessorType
}
