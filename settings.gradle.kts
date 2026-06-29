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
include("entityIndexing")
include("pageFiltering")
include("vendorClient")
include("vendorClient-redis")
include("vendorClient-jpa")