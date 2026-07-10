package cacheStore.mysql.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table

@Entity(name = "cacheStoreEntry")
@Table(name = "cache_store_entry")
class CacheStoreEntryEntity {
    @Id
    @Column(length = 255)
    var cacheKey: String = ""
        private set

    @Column(columnDefinition = "TEXT", nullable = false)
    var value: String = ""
        private set

    @Column
    var expiresAt: Long? = null
        private set

    constructor(cacheKey: String, value: String, expiresAt: Long?) {
        this.cacheKey = cacheKey
        this.value = value
        this.expiresAt = expiresAt
    }

    @PrePersist
    @PreUpdate
    fun validate() {
        val expiry = expiresAt
        if (expiry != null && System.currentTimeMillis() > expiry) {
            throw IllegalStateException("Invalid cache expiry date")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CacheStoreEntryEntity

        return cacheKey == other.cacheKey
    }

    override fun hashCode(): Int = cacheKey.hashCode()
}
