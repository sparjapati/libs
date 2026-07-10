package com.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider

// Resolves EntityLookupService beans lazily (on first exists() call), not eagerly at
// construction. This registry backs entityValidationAdvisor, a global Advisor bean that Spring's
// auto-proxy-creator resolves eagerly on the very first bean it ever proxies — an eager
// `List<EntityLookupService>` here would force every EntityLookupService-implementing bean to be
// fully instantiated at that same early moment, before later-registered advisors (e.g. Spring's
// own caching advisor) exist, permanently freezing those beans' proxies without that advice.
class EntityLookupRegistry(
    private val servicesProvider: ObjectProvider<List<EntityLookupService>>,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(EntityLookupRegistry::class.java)
    }

    private val serviceMap: Map<String, EntityLookupService> by lazy {
        val services = servicesProvider.getObject()
        LOGGER.info("Registered {} entityLookupServices: {}", services.size, services.map { it.getEntity().uppercase() })
        services.associateBy { it.getEntity().uppercase() }
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
