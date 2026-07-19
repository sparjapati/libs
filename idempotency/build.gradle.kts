plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `maven-publish`
    `java-library`
}
dependencies {
    api(platform(libs.spring.framework.bom))
    api(libs.spring.context)
    api(libs.spring.aop)
    api(libs.aspectj.weaver)
    api(libs.jackson.databind)
    implementation(libs.spring.web)
    implementation(libs.slf4j.api)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.jakarta.servlet.api)

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
            artifactId = "idempotency"
        }
    }
}
