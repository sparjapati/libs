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
    api(platform(libs.spring.framework.bom))
    api(libs.bundles.spring.core)
    api(libs.aspectj.weaver)
    api(libs.spring.web)

    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    compileOnly(libs.spring.boot.autoconfigure)
}
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "entityLookup"
        }
    }
}
