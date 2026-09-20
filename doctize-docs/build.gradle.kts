import java.security.MessageDigest

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

// The architecture map of Doctize is a licensed feature, so the site only gets it where there is a license: in doctize-docs/, in
// $DOCTIZE_LICENSE or in ~/.doctize/license (the places Doctize itself looks; see RELEASING.md). Everywhere else, such as CI without
// the secret, the site is built without that page, and without a warning.
val licenseFile = layout.projectDirectory.file("doctize.license").asFile
val homeLicenseFile = File(System.getProperty("user.home"), ".doctize/license")
val environmentLicense = providers.environmentVariable("DOCTIZE_LICENSE").orElse("")
val licensed = environmentLicense.get().isNotBlank() || licenseFile.isFile || homeLicenseFile.isFile

// The architecture diagrams look at every module, not only at the two whose sources are compiled here.
val analyzedModules = listOf(":doctize-cli", ":doctize-processor", ":doctize-gradle-plugin", ":doctize-licensing")

tasks.named<JavaExec>("generateDocs") {
    if (licensed) {
        args("--architecture")
        analyzedModules.forEach { path ->
            val classes = project(path).layout.buildDirectory.dir("classes/java/main")
            dependsOn("$path:classes")
            inputs.dir(classes).withPropertyName("classesOf" + path.removePrefix(":").replace('-', '_'))
            args("--classes", classes.get().asFile.path)
        }
    }
    // A license that appears, changes or goes away changes the site, so it is an input. The one in the environment only as a hash.
    inputs.property("architectureMap", licensed)
    inputs.files(listOf(licenseFile, homeLicenseFile).filter { it.isFile }).withPropertyName("licenseFiles")
    inputs.property("environmentLicense", environmentLicense.map { text: String ->
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
    })
}
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

        // Dependency coordinates, and the plugin declarations: id("dev.doctize") version "x" and id 'dev.doctize' version 'x'.
        val coordinates = Regex("""dev\.doctize:doctize-[a-z-]+:([0-9][^"'\s)]*)""")
        val pluginDeclaration = Regex("""id\s*\(?\s*["']dev\.doctize["']\s*\)?\s+version\s+["']([^"']+)["']""")
        val wrong = guides.files.flatMap { file ->
            val text = file.readText()
            (coordinates.findAll(text) + pluginDeclaration.findAll(text)).filter { it.groupValues[1] != projectVersion }.map { "${file.name}: ${it.value}" }.toList()
        }
        check(wrong.isEmpty()) { "Documentation names another version than $projectVersion:\n" + wrong.joinToString("\n") }
    }
}
tasks.named("check") { dependsOn(verifyDocsVersion) }
