package com.aspect

import com.service.EntityLookupRegistry
import com.service.EntityValidationCache
import com.annotation.Entity
import com.exception.EntityNotFoundException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory

@Aspect
class EntityValidationAspect(
    private val entityLookupRegistry: EntityLookupRegistry,
    private val entityValidationCache: EntityValidationCache
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    fun validateEntity(joinPoint: ProceedingJoinPoint): Any? {
        val method = (joinPoint.signature as MethodSignature).method
        val args = joinPoint.args

        method.parameters.forEachIndexed { idx, param ->
            val annotation = param.getAnnotation(Entity::class.java) ?: return@forEachIndexed
            val idValue = args[idx]
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
        return joinPoint.proceed()
    }
}
