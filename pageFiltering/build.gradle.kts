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
    api(platform(libs.spring.data.bom))
    api(libs.bundles.spring.core)
    api(libs.spring.web)
    api(libs.spring.webmvc)
    api(libs.spring.data.jpa)
    api(libs.jakarta.persistence)
    implementation(libs.slf4j.api)
    compileOnly(libs.spring.boot.autoconfigure)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "pageFiltering"
        }
    }
}
