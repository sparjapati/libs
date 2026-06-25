package com.service

import org.slf4j.LoggerFactory

class EntityLookupRegistry(
    services: List<EntityLookupService>
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(EntityLookupRegistry::class.java)
    }

    private val serviceMap: Map<String, EntityLookupService> = services.associateBy { it.getEntity().uppercase() }

    init {
        LOGGER.info("Registered {} entityLookupServices: {}", services.size, serviceMap.keys)
    }

    fun exists(entityName: String, id: Any): Boolean {
        val service = serviceMap[entityName.uppercase()]
        if (service == null) {
            LOGGER.warn("No entity-lookup service found for entity={} — returning true (permissive fallback)", entityName)
            return true
        }
        val exists = service.exists(id)
        LOGGER.debug("Entity lookup entity={} id={} exists={}", entityName, id, exists)
        return exists
    }
}
