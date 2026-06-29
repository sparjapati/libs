plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `maven-publish`
    `java-library`
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(libs.okhttp3)
    api(libs.retrofit.core)
    api(libs.bundles.resilience4j)
    implementation(libs.slf4j.api)
    compileOnly(libs.spring.boot.autoconfigure)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "vendorClient"
        }
    }
}
