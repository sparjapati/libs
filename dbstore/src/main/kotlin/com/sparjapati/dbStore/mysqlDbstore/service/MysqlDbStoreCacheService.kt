package com.sparjapati.dbStore.mysqlDbstore.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sparjapati.dbStore.modals.DbStoreCache
import com.sparjapati.dbStore.mysqlDbstore.entity.DbStoreCacheEntryEntity
import com.sparjapati.dbStore.mysqlDbstore.entity.toDto
import com.sparjapati.dbStore.mysqlDbstore.repository.MysqlDbStoreCacheRepository
import com.sparjapati.dbStore.service.DbStoreService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

class MysqlDbStoreCacheService(
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

    override fun save(
        cacheKey: String,
        value: Any,
        expiresAt: LocalDateTime?
    ): DbStoreCache {
        return repository.save(
            DbStoreCacheEntryEntity(
                cacheKey = cacheKey,
                value = objectMapper.writeValueAsString(value),
                expiresAt = expiresAt
            )
        ).toDto()
    }

    override fun get(cacheKey: String): DbStoreCache? {
        return repository.findById(cacheKey)
            .map { it.toDto() }
            .getOrNull()
    }

    override fun delete(key: String) {
        repository.deleteById(key)
    }

    override fun deleteAllByPrefix(prefix: String) {
        repository.deleteAllByCacheKeyStartingWith(prefix)
    }
}