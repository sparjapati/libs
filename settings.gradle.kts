pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "libs"
include("dbstore")
include("entityLookup")
include("bulkFileProcessing")
include("bulkFileProcessing-mysql")
include("entityIndexing")
include("pageFiltering")
include("vendorClient")
include("vendorClient-apiconfig-jpa")
include("vendorClient-apilog-jpa")
include("vendorClient-ratelimiter-redis")
include("cacheStore")
include("cacheStore-mysql")
include("cacheStore-mongo")