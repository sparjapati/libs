package com.sparjapati.mysqlDbstore.repository

import com.sparjapati.mysqlDbstore.entity.DbStoreCacheEntryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MysqlDbStoreCacheRepository : JpaRepository<DbStoreCacheEntryEntity, String>