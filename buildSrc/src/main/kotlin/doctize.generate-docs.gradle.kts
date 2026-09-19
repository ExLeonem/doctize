/*
 * Adds a `generateDocs` task that runs the Doctize command line tool of this build on the project's
 * compiled classes, its `doctize.yml` and its `docs/` directory.
 */
plugins {
    `java-library`
}

val doctizeTool: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add("doctizeTool", project(":doctize-cli"))
}

tasks.register<JavaExec>("generateDocs") {
    group = "documentation"
    description = "Generates the documentation site into build/docs."

    val classesDirectory = layout.buildDirectory.dir("classes/java/main")
    val outputDirectory = layout.buildDirectory.dir("docs")
    dependsOn(tasks.named("classes"))
    inputs.dir(classesDirectory)
    inputs.dir("docs")
    inputs.file("doctize.yml")
    outputs.dir(outputDirectory)

    classpath = doctizeTool
    mainClass = "dev.doctize.cli.DoctizeCli"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    args(
        "generate",
        "--config", file("doctize.yml").path,
        "--model", classesDirectory.get().asFile.path,
        "--output", outputDirectory.get().asFile.path,
    )
}
