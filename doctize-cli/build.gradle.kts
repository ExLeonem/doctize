plugins {
    id("doctize.java-conventions")
    id("doctize.publishing-conventions")
    application
}

description = "The doctize command line tool: generates and previews documentation sites."

dependencies {
    implementation(project(":doctize-core"))
    implementation(libs.picocli)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    // Writes the project version into version.properties, so `doctize --version` cannot drift from the release.
    inputs.property("version", project.version)
    filesMatching("**/version.properties") { expand("version" to project.version) }
}

application {
    mainClass = "dev.doctize.cli.DoctizeCli"
    applicationName = "doctize"
}
