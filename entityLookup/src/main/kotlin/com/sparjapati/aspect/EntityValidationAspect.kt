package com.sparjapati.aspect

import com.sparjapati.service.EntityLookupRegistry
import com.sparjapati.service.EntityValidationCache
import com.sparjapati.annotation.Entity
import com.sparjapati.exception.EntityNotFoundException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component

@Aspect
@Component
class EntityValidationAspect(
    private val entityLookupRegistry: EntityLookupRegistry,
    private val entityValidationCache: EntityValidationCache
) {
    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    fun validateEntity(joinPoint: ProceedingJoinPoint): Any? {
        val method = (joinPoint.signature as MethodSignature).method
        val args = joinPoint.args

        method.parameters.forEachIndexed { idx, param ->
            val annotation = param.getAnnotation(Entity::class.java)
            if (annotation != null) {
                val idValue = args[idx]

                val entityName = annotation.name.uppercase()
                val cacheKey = "${entityName}:${idValue}"
                if (entityValidationCache.contains(cacheKey) && !entityValidationCache[cacheKey]) {
                    throw EntityNotFoundException(entityName, idValue.toString())
                }

                val exists = entityLookupRegistry.exists(entityName, idValue)
                entityValidationCache.put(cacheKey, exists)
                if (!exists) {
                    throw EntityNotFoundException(entityName, idValue.toString())
                }
            }
        }
        return joinPoint.proceed();
    }
}