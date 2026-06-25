package com.indexing.aspect

import com.indexing.annotation.ReindexId
import com.indexing.context.ReindexContextHolder
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotationUtils
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Aspect
class ReindexParamAspect {

    private val log = LoggerFactory.getLogger(javaClass)

    // Per-method annotation caches: avoids repeated reflection on every call.
    // Uses AnnotationUtils so annotations declared on interface methods are found
    // even when the bean proxy is CGLIB (which only sees the concrete class method).
    private val methodAnnotationCache = ConcurrentHashMap<Method, Optional<ReindexId>>()
    private val paramAnnotationCache  = ConcurrentHashMap<Pair<Method, Int>, Optional<ReindexId>>()

    @AfterReturning(
        pointcut  = "execution(* *(..)) && !within(com.indexing..*)",
        returning = "returnValue",
    )
    fun collect(joinPoint: JoinPoint, returnValue: Any?) {
        val method = (joinPoint.signature as MethodSignature).method

        if (!ReindexContextHolder.isActive()) {
            if (log.isDebugEnabled) {
                val ignored = mutableMapOf<String, MutableList<String>>()
                methodAnnotationCache.getOrPut(method) {
                    Optional.ofNullable(AnnotationUtils.findAnnotation(method, ReindexId::class.java))
                }.orElse(null)?.let { annotation ->
                    toIds(returnValue)?.let { ids ->
                        ignored.getOrPut(annotation.entity.simpleName ?: "?") { mutableListOf() }.addAll(ids)
                    }
                }
                method.parameters.indices.forEach { index ->
                    paramAnnotationCache.getOrPut(method to index) {
                        Optional.ofNullable(findParamAnnotation(method, index))
                    }.orElse(null)?.let { annotation ->
                        toIds(joinPoint.args[index])?.let { ids ->
                            ignored.getOrPut(annotation.entity.simpleName ?: "?") { mutableListOf() }.addAll(ids)
                        }
                    }
                }
                if (ignored.isNotEmpty()) {
                    log.debug(
                        "Reindex IDs will be ignored — no active @ReindexContext scope method={} entities={}",
                        method.name,
                        ignored,
                    )
                }
            }
            return
        }

        collectFromReturnValue(method, returnValue)
        collectFromParameters(method, joinPoint.args)
    }

    // --- return-value collection ---

    private fun collectFromReturnValue(method: Method, returnValue: Any?) {
        val annotation = methodAnnotationCache.getOrPut(method) {
            Optional.ofNullable(AnnotationUtils.findAnnotation(method, ReindexId::class.java))
        }.orElse(null) ?: return

        when (returnValue) {
            is String     -> {
                log.debug("Collected reindex id from return value method={} entity={} id={}", method.name, annotation.entity.simpleName, returnValue)
                ReindexContextHolder.register(entityClass = annotation.entity, id = returnValue)
            }
            is Collection<*> -> {
                val ids = returnValue.map { it.toString() }
                if (ids.isNotEmpty()) {
                    log.debug("Collected reindex ids from return value method={} entity={} ids={}", method.name, annotation.entity.simpleName, ids)
                    ReindexContextHolder.register(entityClass = annotation.entity, ids = ids)
                }
            }
        }
    }

    // --- parameter collection ---

    private fun collectFromParameters(method: Method, args: Array<Any?>) {
        method.parameters.forEachIndexed { index, _ ->
            val annotation = paramAnnotationCache.getOrPut(method to index) {
                Optional.ofNullable(findParamAnnotation(method, index))
            }.orElse(null) ?: return@forEachIndexed

            when (val value = args[index]) {
                is String     -> {
                    log.debug("Collected reindex id from param method={} entity={} id={}", method.name, annotation.entity.simpleName, value)
                    ReindexContextHolder.register(entityClass = annotation.entity, id = value)
                }
                is Collection<*> -> {
                    val ids = value.map { it.toString() }
                    if (ids.isNotEmpty()) {
                        log.debug("Collected reindex ids from param method={} entity={} ids={}", method.name, annotation.entity.simpleName, ids)
                        ReindexContextHolder.register(entityClass = annotation.entity, ids = ids)
                    }
                }
            }
        }
    }

    private fun toIds(value: Any?): List<String>? = when (value) {
        is String        -> listOf(value)
        is Collection<*> -> value.map { it.toString() }.takeIf { it.isNotEmpty() }
        else             -> null
    }

    // Checks the concrete method parameter first, then searches interface method parameters.
    // Java does not inherit parameter annotations, so CGLIB proxies require the fallback.
    private fun findParamAnnotation(method: Method, paramIndex: Int): ReindexId? {
        method.parameters[paramIndex].getAnnotation(ReindexId::class.java)?.let { return it }
        return method.declaringClass.interfaces.firstNotNullOfOrNull { iface ->
            runCatching { iface.getMethod(method.name, *method.parameterTypes) }.getOrNull()
                ?.parameters?.getOrNull(paramIndex)
                ?.getAnnotation(ReindexId::class.java)
        }
    }
}
