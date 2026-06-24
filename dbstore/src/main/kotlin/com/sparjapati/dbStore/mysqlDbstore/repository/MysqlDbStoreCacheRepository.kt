package com.sparjapati.dbStore.mysqlDbstore.repository

import com.sparjapati.dbStore.mysqlDbstore.entity.DbStoreCacheEntryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MysqlDbStoreCacheRepository : JpaRepository<DbStoreCacheEntryEntity, String> {
    fun deleteAllByCacheKeyStartingWith(prefix: String)
}