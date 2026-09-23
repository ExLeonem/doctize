---
title: Running from Gradle
description: The Doctize Gradle plugin generates the site as part of your build.
keywords: [gradle, plugin, generateDocs, build]
---

# Running from Gradle

The Gradle plugin `dev.doctize` puts Doctize into your build. In a Java project it

- adds the annotations (`compileOnly`) and the annotation processor (`annotationProcessor`), in its own version,
- adds the task `generateDocs`, which compiles your code and generates the site into `build/docs`,
- adds a `doctize { }` block for the few settings you may want to change.

> [!WARNING]
> Doctize is not published to a public repository yet. Until it is, install it locally with
> `./gradlew publishToMavenLocal` and add `mavenLocal()` to the repositories below, in `settings.gradle.kts` as well.

## Apply the plugin

The plugin is published to Maven Central, which Gradle does not search for plugins by default. Add it to the plugin
repositories in `settings.gradle.kts`:

```kotlin title="settings.gradle.kts"
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then apply it. Your build needs `mavenCentral()` (or a mirror) as a repository for the dependencies the plugin adds:

```kotlin tab="Gradle (Kotlin)"
plugins {
    `java-library`
    id("dev.doctize") version "0.1.0"
}

repositories {
    mavenCentral()
}
```

```groovy tab="Gradle (Groovy)"
plugins {
    id 'java-library'
    id 'dev.doctize' version '0.1.0'
}

repositories {
    mavenCentral()
}
```

```console
$ ./gradlew generateDocs      # → build/docs/index.html
```

That is all a project needs. A [`doctize.yml`](04-configuration.md) and a `docs` directory of guides are optional, and
found by their default names. The site is named after the root project, and shows the version and the description of the
project, unless `doctize.yml` sets `project.name`, `project.version` or `project.tagline`:

```kotlin title="build.gradle.kts"
version = "1.2.0"
description = "A small, fast, bounded in-memory cache for Java."
```

### What runs where

| What | Needs |
|------|-------|
| Gradle, and the plugin | Gradle 9 or newer, on a JDK 17 or newer. The plugin is tested with Gradle 9.7. |
| Compiling your code with the processor | A JDK 25 or newer, see [Installation](01-installation.md) |
| The Doctize tool that `generateDocs` runs | Java 25. It runs in a separate process, on a Java 25 toolchain that Gradle finds or downloads. |

If no Java 25 is installed, Gradle can download one when you add a toolchain resolver to `settings.gradle.kts`, for example
`id("org.gradle.toolchains.foojay-resolver-convention")`. Or point the plugin at a JDK you have, see `javaLauncher` below.

## The `doctize` block

Every setting has a default, so you only write the ones you change.

```kotlin tab="Gradle (Kotlin)"
doctize {
    config = layout.projectDirectory.file("doctize.yml")
    guides = layout.projectDirectory.dir("docs")
    output = layout.buildDirectory.dir("docs")
    failOnWarnings = true
}
```

```groovy tab="Gradle (Groovy)"
doctize {
    config = layout.projectDirectory.file('doctize.yml')
    guides = layout.projectDirectory.dir('docs')
    output = layout.buildDirectory.dir('docs')
    failOnWarnings = true
}
```

| Setting | Meaning | Default |
|---------|---------|---------|
| `version` | The version of Doctize used for the tool, the annotations and the processor | The version of the plugin |
| `addDependencies` | Add the annotations and the processor to a Java project. Turn it off if you declare them yourself. | `true` |
| `config` | The configuration file, see [Configuration](04-configuration.md) | `doctize.yml`, if it exists |
| `guides` | The directory with the Markdown guides | `docs`, if it exists |
| `theme` | A custom [theme](../guides/03-theming.md) directory | The built-in theme |
| `output` | Where the site is written | `build/docs` |
| `models` | What the annotation processor produced: class directories, `model.json` files or jars | The main source set |
| `modules(...)` | Documents other Java projects, see [Several modules](#several-modules) | |
| `additionalInputs` | Files that the configuration reads, see [Up-to-date checks](#up-to-date-checks) | none |
| `githubPages` | Prepare the output for [GitHub Pages](05-github-pages.md) | `false` |
| `baseUrl` | The address the site will be published at | none |
| `customDomain` | The custom domain of a GitHub Pages site | none |
| `failOnWarnings` | Fail the build if generating the site produced warnings, such as an unresolved `{@link}` | `false` |
| `arguments` | More arguments for [`doctize generate`](02-command-line.md) | The property `doctizeArgs` |
| `javaLauncher` | The Java that runs the tool, which must be 25 or newer | A Java 25 toolchain |

A value set here is handed to the tool as a command line option, so it wins over the same setting in `doctize.yml`. That
includes the default for `guides`: if your `doctize.yml` says `guides: documentation`, set `guides` in the `doctize` block as well,
or the directory `docs` is used.

### Options of the task

The settings that change from run to run are also options of the task:

```console
$ ./gradlew generateDocs --github-pages --base-url=https://acme.github.io/cache/
$ ./gradlew generateDocs --fail-on-warnings
```

The options are `--github-pages`, `--base-url`, `--custom-domain` and `--fail-on-warnings`. Anything else
can be given as the project property `doctizeArgs`, which is what the workflow of
[Publishing to GitHub Pages](05-github-pages.md) does:

```console
$ ./gradlew generateDocs -PdoctizeArgs="--github-pages --base-url https://acme.github.io/cache/"
```

## Generate the site with every check

The plugin does not hook `generateDocs` into `check`. If broken links should fail your build, do it yourself:

```kotlin tab="Gradle (Kotlin)"
doctize {
    failOnWarnings = true
}

tasks.check {
    dependsOn(tasks.generateDocs)
}
```

```groovy tab="Gradle (Groovy)"
doctize {
    failOnWarnings = true
}

tasks.named('check') {
    dependsOn 'generateDocs'
}
```

## Several modules

Declare the plugin once in the root project, and apply it in every module that has annotated code, so that each of them gets the
annotations and the processor. Then generate one site in a project of its own, which names the modules:

```kotlin title="settings.gradle.kts"
include("core", "client", "site")
```

```kotlin title="build.gradle.kts (root)"
plugins {
    id("dev.doctize") version "0.1.0" apply false
}
```

```kotlin title="core/build.gradle.kts and client/build.gradle.kts"
plugins {
    `java-library`
    id("dev.doctize")
}
```

```kotlin title="site/build.gradle.kts"
plugins {
    id("dev.doctize")
}

doctize {
    modules(project(":core"), project(":client"))
}
```

The `site` project holds `doctize.yml` and the `docs` directory, and `./gradlew :site:generateDocs` compiles both modules and
writes one site. Types of the modules can link to each other. The order in which Gradle configures the projects does not
matter.

`modules(...)` adds the main source sets of the projects to `models`. Like anything you add to `models`, that replaces the default,
which is the main source set of the project itself. A project that has code of its own and also collects others names itself:
`modules(project, project(":client"))`.

## Up-to-date checks

Gradle skips `generateDocs` when nothing changed. It looks at the annotation processor's output, the configuration file, the
guides directory, a custom theme directory, the settings above and the tool itself.

It cannot look into `doctize.yml`. If the file names further files, such as `theme.extraCss`, or a `theme.path` that you did not
repeat in the `doctize` block, add them to `additionalInputs` so that changing them regenerates the site:

```kotlin
doctize {
    additionalInputs.from("branding/brand.css", "branding/brand.js")
}
```

## Without the plugin

The plugin is a thin wrapper: it runs the [command line tool](02-command-line.md). If you cannot apply a plugin, call the
tool from a `JavaExec` task:

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

    classpath = doctizeTool
    mainClass = "dev.doctize.cli.DoctizeCli"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)   // must be JDK 25 or newer
    args("generate", "--config", file("doctize.yml").path,
         "--model", classes.get().asFile.path, "--output", site.get().asFile.path)
}
```

This repository builds its own documentation with a task like that, because a plugin cannot be applied in the build that produces it.

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
