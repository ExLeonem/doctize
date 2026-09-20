plugins {
    `java-gradle-plugin`
    id("doctize.java-conventions")
    id("doctize.publishing-conventions")
}

description = "A Gradle plugin that generates a Doctize documentation site: wires the annotation processor and adds the generateDocs task."

// Gradle loads the plugin into its own JVM, which needs to be 17 or newer only; the tool the plugin drives runs
// on a Java 25 toolchain, in a separate process (see DoctizeGenerate). So the plugin must not require Java 25.
tasks.withType<JavaCompile>().configureEach { options.release = 17 }

// "dev.doctize.gradle-plugin", the default derived from the project name, is not a valid module name.
tasks.jar { manifest.attributes("Automatic-Module-Name" to "dev.doctize.gradle") }

gradlePlugin {
    plugins {
        register("doctize") {
            id = "dev.doctize"
            implementationClass = "dev.doctize.gradle.DoctizePlugin"
            displayName = "Doctize"
            description = "Generates a searchable documentation site from the Javadoc of annotated code and Markdown guides."
            tags = listOf("documentation", "javadoc", "markdown", "site-generator")
        }
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    // The plugin adds the tool and the processor in its own version by default, so it has to know it.
    inputs.property("version", project.version)
    filesMatching("**/version.properties") { expand("version" to project.version) }
}
