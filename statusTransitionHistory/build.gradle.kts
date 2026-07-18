plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `maven-publish`
    `java-library`
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    api(platform(libs.spring.data.bom))
    api(libs.spring.data.commons)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.mockk)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "statusTransitionHistory"
        }
    }
}
