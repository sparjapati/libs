plugins {
    kotlin("jvm")
    `maven-publish`
    `java-library`
}
dependencies {
    api(project(":idempotency"))
    api(libs.lettuce.core)
    compileOnly(libs.spring.boot.autoconfigure)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.jackson.kotlin)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java {
    withSourcesJar()
    withJavadocJar()
}
tasks.test { useJUnitPlatform() }
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "idempotency-redis"
        }
    }
}
