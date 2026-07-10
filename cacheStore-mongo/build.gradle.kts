plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `maven-publish`
    `java-library`
}
java { withSourcesJar(); withJavadocJar() }
dependencies {
    api(project(":cacheStore"))
    api(platform(libs.spring.framework.bom))
    api(platform(libs.spring.data.bom))
    api(libs.spring.data.mongodb)
    compileOnly(libs.spring.boot.autoconfigure)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "cacheStore-mongo"
        }
    }
}
