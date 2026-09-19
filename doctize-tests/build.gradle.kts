/*
 * Tests that span modules. Unit tests live next to the code they test; this module has two more levels:
 *
 *  - integrationTest: in-process. Real javac + the annotation processor + the generator, working together.
 *  - systemTest:      black box. The installed CLI as a subprocess, and the artifacts that would be published,
 *                     consumed the way a downstream build consumes them.
 *
 * Both run as part of `./gradlew check`.
 */
plugins {
    id("doctize.java-conventions")
}

val integrationTest: SourceSet by sourceSets.creating
val systemTest: SourceSet by sourceSets.creating

// Helpers shared by both suites (a crawler for generated sites, javac and process wrappers).
listOf(integrationTest, systemTest).forEach { it.java.srcDir("src/support/java") }
// The sample libraries are used by both suites.
systemTest.resources.srcDir("src/integrationTest/resources")

dependencies {
    for (suite in listOf("integrationTest", "systemTest")) {
        add("${suite}Implementation", platform(libs.junit.bom))
        add("${suite}Implementation", libs.junit.jupiter)
        add("${suite}Implementation", libs.assertj)
        add("${suite}RuntimeOnly", libs.junit.platform.launcher)
    }

    "integrationTestImplementation"(project(":doctize-core"))
    "integrationTestImplementation"(project(":doctize-annotations"))
    "integrationTestRuntimeOnly"(project(":doctize-processor"))
}

// What a downstream build would resolve: the *published* artifacts, from the staging repository, plus their
// transitive dependencies from Maven Central. The system tests run against this classpath, not against project outputs.
repositories {
    maven {
        url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        content { includeGroup("dev.doctize") }
    }
}

val consumerClasspath: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val consumerProcessorPath: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    consumerClasspath("dev.doctize:doctize-annotations:${project.version}")
    consumerProcessorPath("dev.doctize:doctize-processor:${project.version}")
    consumerProcessorPath("dev.doctize:doctize-cli:${project.version}")
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs the cross-module integration tests."
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
}

val systemTestTask = tasks.register<Test>("systemTest") {
    group = "verification"
    description = "Runs the black-box tests against the installed CLI and the staged artifacts."
    testClassesDirs = systemTest.output.classesDirs
    classpath = systemTest.runtimeClasspath
    shouldRunAfter(integrationTestTask)

    // The installed CLI distribution, and the artifacts exactly as they would be uploaded.
    dependsOn(":doctize-cli:installDist", rootProject.tasks.named("stageArtifacts"))
    inputs.files(consumerClasspath, consumerProcessorPath)
    val cliHome = project(":doctize-cli").layout.buildDirectory.dir("install/doctize")
    val stagingRepository = rootProject.layout.buildDirectory.dir("staging-deploy")
    val projectVersion = project.version.toString()
    val signed = providers.gradleProperty("signingInMemoryKey").isPresent
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Ddoctize.cli.home=${cliHome.get().asFile}",
            "-Ddoctize.staging.repository=${stagingRepository.get().asFile}",
            "-Ddoctize.version=$projectVersion",
            "-Ddoctize.signed=$signed",
            "-Ddoctize.consumer.classpath=${consumerClasspath.asPath}",
            "-Ddoctize.consumer.processorpath=${consumerProcessorPath.asPath}",
        )
    })
}

tasks.named("check") { dependsOn(integrationTestTask, systemTestTask) }
