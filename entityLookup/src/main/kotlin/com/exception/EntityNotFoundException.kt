package com.exception
/**
 * use this exception to throw exception when an resourceId not found,
 * received by some user-input
 */
data class EntityNotFoundException(
    private val entityName: String,
    private val entityId: String
) : Exception("No ${entityName.uppercase()} found for id: $entityId")