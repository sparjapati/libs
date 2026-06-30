plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `maven-publish`
    `java-library`
}
java { withSourcesJar(); withJavadocJar() }
dependencies {
    api(project(":vendorClient"))
    api(platform(libs.spring.framework.bom))
    api(platform(libs.spring.data.bom))
    api(libs.spring.data.jpa)
    api(libs.jakarta.persistence)
    implementation(libs.jackson.databind)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.boot.persistence)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "vendorClient-apilog-jpa"
        }
    }
}
