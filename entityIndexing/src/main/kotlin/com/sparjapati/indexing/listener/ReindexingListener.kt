package com.sparjapati.indexing.listener

import com.sparjapati.indexing.event.ReindexBatchEvent
import com.sparjapati.indexing.service.ReindexService
import com.sparjapati.indexing.sink.IndexSink
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener

class ReindexingListener(
    private val reindexService: ReindexService,
    private val sinks: List<IndexSink>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun handle(event: ReindexBatchEvent) {
        log.info("Received reindex event entities={}", event.requests.keys.map { it.simpleName })
        event.requests.forEach { (entityClass, ids) ->
            val documents = reindexService.reindex(entityClass = entityClass, ids = ids)
            sinks.forEach { it.push(entityClass = entityClass, documents = documents) }
        }
    }
}
