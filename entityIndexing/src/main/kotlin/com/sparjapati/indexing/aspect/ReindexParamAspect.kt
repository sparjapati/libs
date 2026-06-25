package com.sparjapati.indexing.aspect

import com.sparjapati.indexing.annotation.ReindexId
import com.sparjapati.indexing.context.ReindexContextHolder
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory

@Aspect
class ReindexParamAspect {

    private val log = LoggerFactory.getLogger(javaClass)

    // Intercepts all Spring-managed service and component beans without coupling to a specific package.
    // The isActive() guard makes this a no-op outside a @ReindexContext scope.
    @AfterReturning("execution(* *(..)) && !within(com.sparjapati.indexing..*)")
    fun collect(joinPoint: JoinPoint) {
        if (!ReindexContextHolder.isActive()) return

        val method = (joinPoint.signature as MethodSignature).method
        val args = joinPoint.args

        method.parameters.forEachIndexed { index, param ->
            val annotation = param.getAnnotation(ReindexId::class.java) ?: return@forEachIndexed

            when (val value = args[index]) {
                is String -> {
                    log.debug(
                        "Collected reindex intent method={} entity={} id={}",
                        method.name, annotation.entity.simpleName, value,
                    )
                    ReindexContextHolder.register(entityClass = annotation.entity, ids = listOf(value))
                }

                is Collection<*> -> {
                    val ids = value.map { it.toString() }
                    if (ids.isNotEmpty()) {
                        log.debug(
                            "Collected reindex intent method={} entity={} ids={}",
                            method.name, annotation.entity.simpleName, ids,
                        )
                        ReindexContextHolder.register(entityClass = annotation.entity, ids = ids)
                    }
                }
            }
        }
    }
}
