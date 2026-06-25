package com.indexing.sink

import com.indexing.core.AbstractEntityIndex
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import kotlin.reflect.KClass

/**
 * Pushes index documents to Elasticsearch via [ElasticsearchOperations].
 * Concrete index classes must be annotated with @Document(indexName = "...").
 * Register this as a @Bean in your application when Spring Data Elasticsearch is on the classpath.
 */
class ElasticsearchIndexSink(
    private val elasticsearchOperations: ElasticsearchOperations,
) : IndexSink {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun push(entityClass: KClass<*>, documents: List<AbstractEntityIndex>) {
        if (documents.isEmpty()) return
        log.debug("Saving to Elasticsearch entity={} documentCount={}", entityClass.simpleName, documents.size)
        elasticsearchOperations.save(documents)
        log.debug("Saved to Elasticsearch entity={} documentCount={}", entityClass.simpleName, documents.size)
    }
}
