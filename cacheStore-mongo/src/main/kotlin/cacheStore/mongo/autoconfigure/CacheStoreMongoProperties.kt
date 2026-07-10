package cacheStore.mongo.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "cache-store.mongo")
data class CacheStoreMongoProperties(
    val defaultTtl: Duration = Duration.ofMinutes(30),
)
