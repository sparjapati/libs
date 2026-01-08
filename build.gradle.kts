plugins {
    kotlin("jvm") version "1.9.22" apply false
    kotlin("plugin.spring") version "1.9.22" apply false
    kotlin("plugin.jpa") version "1.9.22" apply false
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
