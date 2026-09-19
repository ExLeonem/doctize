---
title: Running from Gradle
description: Generate the site as part of your build.
---

# Running from Gradle

Doctize has no Gradle plugin yet; the command line tool is easy to call from a `JavaExec` task. This is
the task this repository uses for its own documentation:

```kotlin title="build.gradle.kts"
val doctizeTool: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compileOnly("dev.doctize:doctize-annotations:0.1.0")
    annotationProcessor("dev.doctize:doctize-processor:0.1.0")
    doctizeTool("dev.doctize:doctize-cli:0.1.0")
}

tasks.register<JavaExec>("generateDocs") {
    group = "documentation"
    val classes = layout.buildDirectory.dir("classes/java/main")
    val site = layout.buildDirectory.dir("docs")

    dependsOn(tasks.classes)
    inputs.dir(classes)
    inputs.dir("docs")
    inputs.file("doctize.yml")
    outputs.dir(site)

    // Extra arguments: ./gradlew generateDocs -PdoctizeArgs="--github-pages --base-url https://..."
    val extraArguments = providers.gradleProperty("doctizeArgs").map { it.trim().split(Regex("\\s+")) }.orElse(emptyList())
    inputs.property("doctizeArgs", extraArguments)
    argumentProviders.add(CommandLineArgumentProvider { extraArguments.get() })

    classpath = doctizeTool
    mainClass = "dev.doctize.cli.DoctizeCli"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)   // must be JDK 25 or newer
    args("generate", "--config", file("doctize.yml").path,
         "--model", classes.get().asFile.path, "--output", site.get().asFile.path)
}
```

Declaring `inputs` and `outputs` makes Gradle skip the task when nothing changed. The `doctizeArgs` property lets you
add arguments to a single run, for example the options of [Publishing to GitHub Pages](05-github-pages.md).

## Several modules

Give `--model` once per module, so that types from all of them end up in one site:

```kotlin
args("generate", "--config", "doctize.yml",
     "--model", project(":core").layout.buildDirectory.dir("classes/java/main").get().asFile.path,
     "--model", project(":client").layout.buildDirectory.dir("classes/java/main").get().asFile.path)
```

## Without the command line tool

Everything the command does is available as a library, {@link Doctize}. Add `dev.doctize:doctize-core`
and call it from a `main` method, a test, or your own Gradle task:

```java
GenerationReport report = Doctize.builder()
        .configFile(Path.of("doctize.yml"))
        .model(Path.of("build/classes/java/main"))
        .output(Path.of("build/docs"))
        .build()
        .generate();

System.out.println(report.pages() + " pages in " + report.sections() + " sections");
```

See {@link Doctize.Builder} for all settings.
