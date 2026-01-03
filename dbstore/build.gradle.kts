plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `maven-publish`
    `java-library`
}
dependencies {
    api(platform(libs.spring.framework.bom))
    api(platform(libs.spring.data.bom))
    api(libs.bundles.spring.core)
    api(libs.spring.data.jpa)
    api(libs.jakarta.persistence)
    api(libs.aspectj.weaver)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
}9053204221
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "dbstore"
        }
    }
}
