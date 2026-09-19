/*
 * Conventions shared by every module: Java 25, strict compiler settings, JUnit 5, reproducible archives
 * and a coverage report. Publishing is added separately by `doctize.publishing-conventions`.
 */
plugins {
    `java-library`
    jacoco
}

group = "dev.doctize"
// `version` comes from gradle.properties; the release workflow checks it against the git tag.

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-Xlint:-processing"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The same sources produce byte-identical archives, so a release can be reproduced and verified.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Doctize",
        )
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required = true
        html.required = true
    }
}
tasks.withType<Test>().configureEach { finalizedBy(tasks.named("jacocoTestReport")) }
