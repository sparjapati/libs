package com.indexing.service

import com.indexing.core.AbstractEntityIndex
import com.indexing.core.IndexConverter
import com.indexing.core.IndexConverterRegistry
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KClass

class ReindexService(
    @PersistenceContext
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

        log.info("Reindex started entity={} idsCount={}", entityClass.simpleName, ids.size)

        val results = ids.chunked(chunkSize).flatMap { chunk ->
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

        log.info("Reindex finished entity={} indexedCount={}", entityClass.simpleName, results.size)

        return results
    }
}
