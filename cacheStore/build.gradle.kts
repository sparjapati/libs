plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `maven-publish`
    `java-library`
}
java { withSourcesJar(); withJavadocJar() }
dependencies {
    api(platform(libs.spring.framework.bom))
    api(libs.spring.context)
    api(libs.jackson.databind)
    implementation(libs.slf4j.api)
    compileOnly(libs.spring.boot.autoconfigure)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.jackson.kotlin)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "cacheStore"
        }
    }
}
