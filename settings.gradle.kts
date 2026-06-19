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