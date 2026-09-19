plugins {
    id("doctize.java-conventions")
    id("doctize.generate-docs")
}

// Doctize documents itself. The processor cannot run inside the modules it is built from (it depends on
// them), so this module compiles their sources a second time, with the annotation processor enabled. Only
// the extracted documentation is used; the compiled classes are not published.
sourceSets.main {
    java.srcDir("../doctize-annotations/src/main/java")
    java.srcDir("../doctize-core/src/main/java")
}

dependencies {
    // The libraries the sources of doctize-core need to compile.
    compileOnly(libs.jackson.databind)
    compileOnly(libs.jackson.yaml)
    compileOnly(libs.commonmark)
    compileOnly(libs.commonmark.tables)
    compileOnly(libs.commonmark.strikethrough)
    compileOnly(libs.commonmark.alerts)
    compileOnly(libs.commonmark.autolink)
    compileOnly(libs.commonmark.tasklist)
    compileOnly(libs.jmustache)

    annotationProcessor(project(":doctize-processor"))
}

// The site is generated with: ./gradlew :doctize-docs:generateDocs

// Generating the documentation is part of `check`, and it must be free of warnings: a broken {@link} fails the build.
tasks.named<JavaExec>("generateDocs") { args("--fail-on-warnings") }
tasks.named("check") { dependsOn("generateDocs") }

// The documentation names the version of Doctize in its configuration and in its installation snippets.
// A release must not ship docs that name another one.
val verifyDocsVersion by tasks.registering {
    group = "verification"
    description = "Checks that the version named in the documentation is the project version."
    val projectVersion = project.version.toString()
    val configuration = layout.projectDirectory.file("doctize.yml")
    val guides = fileTree("docs") { include("**/*.md") }
    inputs.property("version", projectVersion)
    inputs.file(configuration)
    inputs.files(guides)
    doLast {
        val declared = configuration.asFile.readLines()
            .firstOrNull { it.trim().startsWith("version:") }?.substringAfter(":")?.trim()
        check(declared == projectVersion) { "doctize.yml says version $declared, the project is $projectVersion" }

        val coordinates = Regex("""dev\.doctize:doctize-[a-z]+:([0-9][^"'\s)]*)""")
        val wrong = guides.files.flatMap { file ->
            coordinates.findAll(file.readText()).filter { it.groupValues[1] != projectVersion }.map { "${file.name}: ${it.value}" }
        }
        check(wrong.isEmpty()) { "Documentation names another version than $projectVersion:\n" + wrong.joinToString("\n") }
    }
}
tasks.named("check") { dependsOn(verifyDocsVersion) }
