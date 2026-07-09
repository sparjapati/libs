package com.dbStore.service

import com.dbStore.modals.DbStoreCache

interface DbStoreService {
    fun save(cacheKey: String, value: Any, expiresAt: Long? = null): DbStoreCache
    operator fun get(cacheKey: String): DbStoreCache?
    fun delete(key: String)
    fun deleteAllByPrefix(prefix: String)
}