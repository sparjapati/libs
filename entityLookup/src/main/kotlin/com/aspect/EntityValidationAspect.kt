package com.aspect

import com.service.EntityLookupRegistry
import com.service.EntityValidationCache
import com.annotation.Entity
import com.exception.EntityNotFoundException
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory

class EntityValidationAspect(
    private val entityLookupRegistry: EntityLookupRegistry,
    private val entityValidationCache: EntityValidationCache,
) : MethodInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    override fun invoke(invocation: MethodInvocation): Any? {
        val method = invocation.method
        val args = invocation.arguments as Array<Any?>

        method.parameters.forEachIndexed { idx, param ->
            val annotation = param.getAnnotation(Entity::class.java) ?: return@forEachIndexed
            val idValue = args[idx] ?: return@forEachIndexed
            val entityName = annotation.name.uppercase()
            val cacheKey = "${entityName}:${idValue}"

            if (entityValidationCache.contains(cacheKey)) {
                if (!entityValidationCache[cacheKey]) {
                    log.debug(
                        "Entity validation: cached negative result entity={} id={} — throwing EntityNotFoundException",
                        entityName, idValue,
                    )
                    throw EntityNotFoundException(entityName, idValue.toString())
                }
                log.debug("Entity validation: cache hit entity={} id={} exists=true", entityName, idValue)
                return@forEachIndexed
            }

            log.debug("Entity validation: cache miss, querying lookup entity={} id={}", entityName, idValue)
            val exists = entityLookupRegistry.exists(entityName, idValue)
            entityValidationCache.put(cacheKey, exists)
            if (!exists) {
                log.debug(
                    "Entity validation: not found entity={} id={} — throwing EntityNotFoundException",
                    entityName, idValue,
                )
                throw EntityNotFoundException(entityName, idValue.toString())
            }
            log.debug("Entity validation: validated entity={} id={}", entityName, idValue)
        }
        return invocation.proceed()
    }
}
