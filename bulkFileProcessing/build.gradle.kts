plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `maven-publish`
    `java-library`
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    api(libs.spring.boot.batch)
    api(libs.poi.ooxml)
    api(libs.commons.csv)
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly("org.springframework:spring-web")
    implementation(libs.slf4j.api)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
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
            artifactId = "bulkFileProcessing"
        }
    }
}
