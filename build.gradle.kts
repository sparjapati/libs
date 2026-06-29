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
project(":entityIndexing") {
    version = property("entityIndexingVersion") as String
}
project(":pageFiltering") {
    version = property("pageFilteringVersion") as String
}
project(":vendorClient") {
    version = property("vendorClientVersion") as String
}
project(":vendorClient-redis") {
    version = property("vendorClientVersion") as String
}
project(":vendorClient-jpa") {
    version = property("vendorClientVersion") as String
}
