/*
 * Everything a module needs to be published to Maven Central: sources and javadoc jars, the metadata Central
 * requires in the POM, PGP signatures, and a local staging repository that becomes the upload bundle
 * (see the root build file and RELEASING.md).
 */
plugins {
    `java-library`
    `maven-publish`
    signing
}

val repositoryUrl = "https://github.com/ExLeonem/doctize"

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.jar {
    // A stable module name for users on the module path, independent of the jar's file name.
    manifest.attributes("Automatic-Module-Name" to "dev.doctize." + project.name.removePrefix("doctize-"))
    // The license travels with the code.
    from(rootProject.layout.projectDirectory.file("LICENSE")) { into("META-INF") }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
        addBooleanOption("html5", true)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = provider { "Doctize: ${project.name.removePrefix("doctize-")}" }
                description = provider { project.description }
                url = repositoryUrl
                inceptionYear = "2026"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "ExLeonem"
                        name = "ExLeonem"
                        url = "https://github.com/ExLeonem"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/ExLeonem/doctize.git"
                    developerConnection = "scm:git:ssh://git@github.com/ExLeonem/doctize.git"
                    url = repositoryUrl
                }
                issueManagement {
                    system = "GitHub Issues"
                    url = "$repositoryUrl/issues"
                }
            }
        }
    }
    repositories {
        // The upload bundle for Maven Central is assembled from this directory; see the root build file.
        maven {
            name = "staging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

// Signing is required by Central, but only when a key is supplied (CI passes it through
// ORG_GRADLE_PROJECT_signingInMemoryKey and ORG_GRADLE_PROJECT_signingInMemoryKeyPassword).
// Local builds and `publishToMavenLocal` work without one.
val signingKey = providers.gradleProperty("signingInMemoryKey")
val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword")

signing {
    isRequired = false
    if (signingKey.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull)
        sign(publishing.publications["maven"])
    }
}
