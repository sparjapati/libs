plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    kotlin("plugin.jpa") version "2.2.21" apply false
}
subprojects {
    group = property("group") as String

    repositories {
        mavenCentral()
    }
}
project(":dbstore") {
    version = property("dbstoreVersion") as String
}
project(":entityLookup") {
    version = property("entityLookupVersion") as String
}
project(":bulkFileProcessing") {
    version = property("bulkFileProcessingVersion") as String
}
project(":bulkFileProcessing-mysql") {
    version = property("bulkFileProcessingMysqlVersion") as String
}
project(":entityIndexing") {
    version = property("entityIndexingVersion") as String
}
project(":pageFiltering") {
    version = property("pageFilteringVersion") as String
}
project(":vendorClient") {
    version = property("vendorClientVersion") as String
}
project(":vendorClient-apiconfig-jpa") {
    version = property("vendorClientApiConfigJpaVersion") as String
}
project(":vendorClient-apilog-jpa") {
    version = property("vendorClientApiLogJpaVersion") as String
}
project(":vendorClient-ratelimiter-redis") {
    version = property("vendorClientRateLimiterRedisVersion") as String
}
project(":cacheStore") {
    version = property("cacheStoreVersion") as String
}
project(":cacheStore-mysql") {
    version = property("cacheStoreMysqlVersion") as String
}
project(":cacheStore-mongo") {
    version = property("cacheStoreMongoVersion") as String
}
project(":statusTransitionHistory") {
    version = property("statusTransitionHistoryVersion") as String
}
project(":statusTransitionHistory-mysql") {
    version = property("statusTransitionHistoryMysqlVersion") as String
}
project(":idempotency") {
    version = property("idempotencyVersion") as String
}
