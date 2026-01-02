package com.sparjapati.service

import com.sparjapati.modals.DbStoreCache
import java.time.LocalDateTime

interface DbStoreService {
    fun save(cacheKey: String, value: Any, expiresAt: LocalDateTime? = null): DbStoreCache
    operator fun get(cacheKey: String): DbStoreCache?
    fun delete(key: String)
}