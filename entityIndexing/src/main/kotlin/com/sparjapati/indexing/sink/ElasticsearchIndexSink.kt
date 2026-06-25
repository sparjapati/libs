package com.sparjapati.indexing.sink

import com.sparjapati.indexing.core.AbstractEntityIndex
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

    override fun push(entityClass: KClass<*>, documents: List<AbstractEntityIndex>) {
        if (documents.isEmpty()) return
        elasticsearchOperations.save(documents)
    }
}
