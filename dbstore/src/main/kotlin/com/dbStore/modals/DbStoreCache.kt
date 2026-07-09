package com.dbStore.modals

data class DbStoreCache(
    val cacheKey: String,
    val value: String,
    val expiresAt: Long? = null
)