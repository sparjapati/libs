package com.sparjapati.mysqlDbstore.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sparjapati.modals.DbStoreCache
import com.sparjapati.mysqlDbstore.entity.DbStoreCacheEntryEntity
import com.sparjapati.mysqlDbstore.entity.toDto
import com.sparjapati.mysqlDbstore.repository.MysqlDbStoreCacheRepository
import com.sparjapati.service.DbStoreService
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

@Service
class MysqlDbStoreCacheService(
    private val repository: MysqlDbStoreCacheRepository,
) : DbStoreService {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
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

}