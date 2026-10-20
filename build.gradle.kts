plugins {
    `java-library`
    `maven-publish`
}

group = "com.example.vault"
version = "0.1.0"
description = "Java client for the document store"

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // Internal only: no Jackson type appears in the public API, so callers can
    // use Jackson 2 or 3 (Spring Boot 3 or 4) without conflicts.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    // Java 17 so older services can use the client too.
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "document-store-client"
            from(components["java"])
        }
    }
    repositories {
        // ./gradlew :client:publish -PinternalRepoUrl=... -PinternalUsername=... -PinternalPassword=...
        maven {
            name = "internal"
            url = uri(providers.gradleProperty("internalRepoUrl").getOrElse("https://artifactory.example.invalid/libs-release-local"))
            credentials(PasswordCredentials::class)
        }
    }
}
