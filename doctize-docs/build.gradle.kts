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

// The theming guide links to a sample of every ready-made theme: the example library's site, generated once per theme into
// build/docs/samples/<theme>/. The samples run after generateDocs, which would otherwise delete them when it clears its output.
val themeNames = listOf("default") + rootProject.file("doctize-themes/src/main/resources/dev/doctize/themes")
    .listFiles { file -> file.isDirectory }.orEmpty().map { it.name }.sorted()
val exampleDirectory = rootProject.layout.projectDirectory.dir("doctize-example")
val exampleClasses = exampleDirectory.dir("build/classes/java/main")
val samplesDirectory = layout.buildDirectory.dir("docs/samples")

val generateThemeSamples by tasks.registering {
    group = "documentation"
    description = "Generates the example site in every ready-made theme, as samples for the theming guide."
}
themeNames.forEach { theme ->
    val sample = tasks.register<JavaExec>("generateThemeSample" + theme.replaceFirstChar { it.uppercase() }) {
        group = "documentation"
        description = "Generates the example site in the theme '$theme' into build/docs/samples/$theme."
        val output = samplesDirectory.map { it.dir(theme) }
        dependsOn(":doctize-example:classes")
        mustRunAfter("generateDocs")
        inputs.dir(exampleClasses)
        inputs.dir(exampleDirectory.dir("docs"))
        inputs.file(exampleDirectory.file("doctize.yml"))
        outputs.dir(output)

        classpath = configurations["doctizeTool"]
        mainClass = "dev.doctize.cli.DoctizeCli"
        javaLauncher = javaToolchains.launcherFor(java.toolchain)
        args(
            "generate", "--fail-on-warnings",
            "--config", exampleDirectory.file("doctize.yml").asFile.path,
            "--model", exampleClasses.asFile.path,
            "--theme-name", theme,
            "--output", output.get().asFile.path,
        )
    }
    generateThemeSamples { dependsOn(sample) }
}
tasks.named("generateDocs") { finalizedBy(generateThemeSamples) }

// Every theme has a sample, and the guide links to each of them.
val verifyThemeSamples by tasks.registering {
    group = "verification"
    description = "Checks that the theming guide links to a generated sample of every ready-made theme."
    dependsOn(generateThemeSamples)
    val guide = layout.projectDirectory.file("docs/guides/03-theming.md")
    val samples = samplesDirectory
    inputs.file(guide)
    inputs.property("themes", themeNames)
    doLast {
        val text = guide.asFile.readText()
        val problems = themeNames.flatMap { theme ->
            listOfNotNull(
                "03-theming.md has no link to ../samples/$theme/index.html".takeUnless { text.contains("href=\"../samples/$theme/index.html\"") },
                "the sample of '$theme' was not generated".takeUnless { samples.get().file("$theme/index.html").asFile.isFile },
            )
        }
        check(problems.isEmpty()) { "Theme samples:\n" + problems.joinToString("\n") }
    }
}
tasks.named("check") { dependsOn(verifyThemeSamples) }
