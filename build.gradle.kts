plugins {
    kotlin("jvm") version "1.9.22" apply false
    kotlin("plugin.spring") version "1.9.22" apply false
    kotlin("plugin.jpa") version "1.9.22" apply false
}
subprojects {

    group = "com.sparjapati"
    version = "0.0.1"

    repositories {
        mavenCentral()
    }
}