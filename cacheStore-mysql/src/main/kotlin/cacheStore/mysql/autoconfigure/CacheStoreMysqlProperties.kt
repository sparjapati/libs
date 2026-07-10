package cacheStore.mysql.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "cache-store.mysql")
data class CacheStoreMysqlProperties(
    val defaultTtl: Duration = Duration.ofMinutes(30),
)
