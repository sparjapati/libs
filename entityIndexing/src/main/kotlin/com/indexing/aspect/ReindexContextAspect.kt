package com.indexing.aspect

import com.indexing.annotation.ReindexContext
import com.indexing.annotation.ReindexPropagation
import com.indexing.context.ReindexContextHolder
import com.indexing.listener.EntityIndexListenerRegistry
import com.indexing.service.ReindexService
import com.indexing.sink.IndexSink
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

// Ordered just outside @Transactional (LOWEST_PRECEDENCE) so this advice always wraps the
// transaction boundary — see EntityIndexingConfiguration for how the order is applied.
//
// Per-method annotation cache mirrors how Spring resolves @Transactional, so @ReindexContext
// on interface methods is found via AnnotationUtils regardless of proxy type (JDK or CGLIB).
class ReindexContextAspect(
    private val reindexService: ReindexService,
    private val sinks: List<IndexSink>,
    private val listenerRegistry: EntityIndexListenerRegistry,
) : MethodInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val annotationCache = ConcurrentHashMap<Method, Optional<ReindexContext>>()

    override fun invoke(invocation: MethodInvocation): Any? {
        val method = invocation.method
        val reindexContext = annotationCache.getOrPut(method) {
            Optional.ofNullable(AnnotationUtils.findAnnotation(method, ReindexContext::class.java))
        }.orElse(null) ?: return invocation.proceed()

        val isRoot = when (reindexContext.propagation) {
            ReindexPropagation.REQUIRED     -> !ReindexContextHolder.isActive()
            ReindexPropagation.REQUIRES_NEW -> true
        }

        if (isRoot) {
            if (ReindexContextHolder.isActive()) {
                log.debug("Suspending outer reindex context, starting fresh scope method={}", method.name)
            } else {
                log.debug("Starting reindex context method={}", method.name)
            }
            ReindexContextHolder.start()
        } else {
            log.debug("Joining outer reindex context method={}", method.name)
        }

        var completed = false
        try {
            val result = invocation.proceed()
            completed = true
            return result
        } finally {
            if (isRoot) {
                val requests = ReindexContextHolder.finish()
                ReindexContextHolder.clear()

                if (!completed || requests.isEmpty()) {
                    log.debug("Reindex context finished with no entities collected")
                } else {
                    log.info(
                        "Reindex context finished entities={} totalIds={}",
                        requests.keys.map { it.simpleName },
                        requests.values.sumOf { it.size },
                    )

                    if (TransactionSynchronizationManager.isActualTransactionActive()) {
                        TransactionSynchronizationManager.registerSynchronization(
                            object : TransactionSynchronization {
                                override fun afterCommit() {
                                    log.debug("Transaction committed, triggering reindex")
                                    requests.forEach { (entityClass, ids) -> reindex(entityClass, ids) }
                                }
                            }
                        )
                    } else {
                        log.debug("No active transaction, triggering reindex immediately")
                        requests.forEach { (entityClass, ids) -> reindex(entityClass, ids) }
                    }
                }
            }
        }
    }

    private fun reindex(entityClass: KClass<*>, ids: Set<String>) {
        log.debug("Reindex triggered entity={} idCount={}", entityClass.simpleName, ids.size)
        val documents = reindexService.reindex(entityClass = entityClass, ids = ids)
        if (documents.isEmpty()) {
            log.debug("No documents produced for entity={} — sinks and listeners will not be called", entityClass.simpleName)
            return
        }
        sinks.forEach { sink ->
            log.debug("Pushing to sink={} entity={} documentCount={}", sink.javaClass.simpleName, entityClass.simpleName, documents.size)
            sink.push(entityClass = entityClass, documents = documents)
        }
        listenerRegistry.getFor(entityClass).also { listeners ->
            if (listeners.isEmpty()) {
                log.debug("No EntityIndexListeners registered for entity={}", entityClass.simpleName)
            }
        }.forEach { listener ->
            log.debug("Notifying listener={} entity={} documentCount={}", listener.javaClass.simpleName, entityClass.simpleName, documents.size)
            listener.onReindex(documents)
        }
    }
}
