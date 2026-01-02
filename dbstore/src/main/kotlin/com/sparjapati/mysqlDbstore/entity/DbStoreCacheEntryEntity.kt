package com.sparjapati.mysqlDbstore.entity

import com.sparjapati.modals.DbStoreCache
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity(name = "dbStoreCache")
@Table(name = "dbStoreCache")
class DbStoreCacheEntryEntity {
    @Id
    @Column
    var cacheKey: String = ""
        private set

    @Column(columnDefinition = "TEXT", nullable = false)
    var value: String = ""
        private set

    @Column
    var expiresAt: LocalDateTime? = null
        private set

    constructor(cacheKey: String, value: String, expiresAt: LocalDateTime?) {
        this.cacheKey = cacheKey
        this.value = value
        this.expiresAt = expiresAt
    }

    @PrePersist
    @PreUpdate
    fun validate() {
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            throw IllegalStateException("Invalid cache expiry date")
        }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DbStoreCacheEntryEntity

        return cacheKey == other.cacheKey
    }

    override fun hashCode(): Int {
        return cacheKey.hashCode()
    }

}

fun DbStoreCacheEntryEntity.toDto() = DbStoreCache(
    cacheKey = cacheKey,
    value = value,
    expiresAt = expiresAt
)