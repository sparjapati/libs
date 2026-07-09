package com.indexing.service

import com.indexing.core.AbstractEntityIndex
import com.indexing.core.IndexConverter
import com.indexing.core.IndexConverterRegistry
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KClass

open class ReindexService(
    private val entityManager: EntityManager,
    private val converterRegistry: IndexConverterRegistry,
    private val chunkSize: Int = 50,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    @Suppress("UNCHECKED_CAST")
    fun reindex(entityClass: KClass<*>, ids: Collection<String>): List<AbstractEntityIndex> {
        if (ids.isEmpty()) return emptyList()

        val converter = converterRegistry.get<Any, AbstractEntityIndex>(entityClass)
        val domainClass = entityClass.java as Class<Any>
        val cb = entityManager.criteriaBuilder

        val chunks = ids.chunked(chunkSize)
        log.info("Reindex started entity={} idsCount={} chunks={}", entityClass.simpleName, ids.size, chunks.size)

        val results = chunks.flatMapIndexed { chunkIndex, chunk ->
            log.debug("Querying chunk {}/{} entity={} size={}", chunkIndex + 1, chunks.size, entityClass.simpleName, chunk.size)
            val cq = cb.createQuery(domainClass)
            val root = cq.from(domainClass)

            cq.select(root).where(
                cb.and(
                    root.get<String>("id").`in`(chunk),
                    cb.isFalse(root.get<Boolean>("isDeleted")),
                )
            )

            (converter as IndexConverter<Any, AbstractEntityIndex>)
                .convertAll(entityManager.createQuery(cq).resultList)
        }

        val skipped = ids.size - results.size
        if (skipped > 0) {
            log.debug("Reindex skipped {} id(s) for entity={} (not found or isDeleted=true)", skipped, entityClass.simpleName)
        }
        log.info("Reindex finished entity={} indexedCount={}", entityClass.simpleName, results.size)

        return results
    }
}
