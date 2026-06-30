plugins {
    kotlin("jvm")
    `maven-publish`
    `java-library`
}
java { withSourcesJar(); withJavadocJar() }
dependencies {
    api(project(":vendorClient"))
    api(libs.lettuce.core)
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
            artifactId = "vendorClient-ratelimiter-redis"
        }
    }
}
