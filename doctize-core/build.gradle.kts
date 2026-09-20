plugins {
    id("doctize.java-conventions")
    id("doctize.publishing-conventions")
}

description = "Generates a searchable static documentation site from Javadoc and Markdown."

dependencies {
    // The public API is annotated with @Doc so that Doctize can document itself (see doctize-docs).
    compileOnly(project(":doctize-annotations"))

    api(libs.jackson.databind)

    implementation(libs.jackson.yaml)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.commonmark.alerts)
    implementation(libs.commonmark.autolink)
    implementation(libs.commonmark.tasklist)
    implementation(libs.jmustache)

    // The tests compile small projects that use the annotations, as the code that Doctize analyzes does.
    testImplementation(project(":doctize-annotations"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The search engine of the default theme is plain JavaScript; its tests run on Node.js when available.
val searchEngineTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the JavaScript tests of the search engine (skipped if Node.js is not installed)."
    val tests = layout.projectDirectory.dir("src/test/js")
    inputs.dir(tests)
    inputs.dir(layout.projectDirectory.dir("src/main/resources/dev/doctize/theme/default/assets/js"))
    outputs.upToDateWhen { true }
    workingDir(tests)
    commandLine("node", "--test")
    onlyIf {
        runCatching { ProcessBuilder("node", "--version").redirectErrorStream(true).start().waitFor() == 0 }
            .getOrDefault(false)
    }
}

tasks.check { dependsOn(searchEngineTest) }
