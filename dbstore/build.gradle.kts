plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `maven-publish`
    `java-library`
}
dependencies {
    api(platform("org.springframework:spring-framework-bom:6.1.4"))
    api(platform("org.springframework.data:spring-data-bom:2023.1.2"))
    api("org.springframework:spring-context")
    api("org.springframework:spring-aop")
    api("org.springframework:spring-tx")
    api("jakarta.persistence:jakarta.persistence-api:3.1.0")
    api("org.springframework.data:spring-data-jpa")
    api("org.aspectj:aspectjweaver:1.9.22.1")

    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("org.slf4j:slf4j-api:2.0.12")
}
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.sparjapati"
            artifactId = "dbstore"
            version = "0.0.1"
        }
    }
}
