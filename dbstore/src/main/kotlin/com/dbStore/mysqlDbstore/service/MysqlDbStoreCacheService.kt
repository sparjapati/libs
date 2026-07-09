package com.dbStore.mysqlDbstore.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.dbStore.modals.DbStoreCache
import com.dbStore.mysqlDbstore.entity.DbStoreCacheEntryEntity
import com.dbStore.mysqlDbstore.entity.toDto
import com.dbStore.mysqlDbstore.repository.MysqlDbStoreCacheRepository
import com.dbStore.service.DbStoreService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

open class MysqlDbStoreCacheService(
    private val repository: MysqlDbStoreCacheRepository,
    private val objectMapper: ObjectMapper,
) : DbStoreService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MysqlDbStoreCacheService::class.java)
    }

    @PostConstruct
    fun postConstruct() {
        LOGGER.info("MysqlDbStoreCacheService initialized!")
    }

    @Transactional
    override fun save(
        cacheKey: String,
        value: Any,
        expiresAt: Long?
    ): DbStoreCache {
        return repository.save(
            DbStoreCacheEntryEntity(
                cacheKey = cacheKey,
                value = objectMapper.writeValueAsString(value),
                expiresAt = expiresAt
            )
        ).toDto()
    }

    @Transactional(readOnly = true)
    override fun get(cacheKey: String): DbStoreCache? {
        return repository.findById(cacheKey)
            .map { it.toDto() }
            .getOrNull()
    }

    @Transactional
    override fun delete(key: String) {
        repository.deleteById(key)
    }

    @Transactional
    override fun deleteAllByPrefix(prefix: String) {
        repository.deleteAllByCacheKeyStartingWith(prefix)
    }
}