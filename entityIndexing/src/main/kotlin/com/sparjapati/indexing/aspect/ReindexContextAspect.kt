package com.sparjapati.indexing.aspect

import com.sparjapati.indexing.context.ReindexContextHolder
import com.sparjapati.indexing.event.ReindexBatchEvent
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Aspect
class ReindexContextAspect(private val publisher: ApplicationEventPublisher) {

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
                                    log.debug("Transaction committed, publishing reindex event")
                                    publisher.publishEvent(ReindexBatchEvent(requests = requests))
                                }
                            }
                        )
                    } else {
                        log.debug("No active transaction, publishing reindex event immediately")
                        publisher.publishEvent(ReindexBatchEvent(requests = requests))
                    }
                }
            }
        }
    }
}
