package com.sparjapati.indexing.aspect

import com.sparjapati.indexing.context.ReindexContextHolder
import com.sparjapati.indexing.listener.EntityIndexListenerRegistry
import com.sparjapati.indexing.service.ReindexService
import com.sparjapati.indexing.sink.IndexSink
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.reflect.KClass

// Ordered just outside @Transactional (LOWEST_PRECEDENCE) so this aspect always wraps the
// transaction boundary. When the finally block runs, an active transaction means we are inside
// an outer caller's transaction → use afterCommit. No active transaction means the method's
// own transaction already committed → call reindex immediately.
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE - 1)
class ReindexContextAspect(
    private val reindexService: ReindexService,
    private val sinks: List<IndexSink>,
    private val listenerRegistry: EntityIndexListenerRegistry,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(com.sparjapati.indexing.annotation.ReindexContext)")
    fun around(pjp: ProceedingJoinPoint): Any? {

        val isRoot = !ReindexContextHolder.isActive()

        if (isRoot) {
            log.debug("Starting reindex context method={}", pjp.signature)
            ReindexContextHolder.start()
        }

        var completed = false
        try {
            val result = pjp.proceed()
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
        log.info("Reindex triggered entity={}", entityClass.simpleName)
        val documents = reindexService.reindex(entityClass = entityClass, ids = ids)
        sinks.forEach { it.push(entityClass = entityClass, documents = documents) }
        listenerRegistry.getFor(entityClass).forEach { it.onReindex(documents) }
    }
}
