package com.sparjapati.dbStore.modals

import java.time.LocalDateTime

data class DbStoreCache(
    val cacheKey: String,
    val value: String,
    val expiresAt: LocalDateTime? = null
)