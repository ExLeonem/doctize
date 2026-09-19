---
title: Installation
description: Requirements, modules and how to get them.
---

# Installation

## Requirements

| What | Requirement |
|------|-------------|
| JDK that runs `javac` (the annotation processor runs inside it) | 25 or newer |
| JDK that runs the `doctize` command | 25 or newer |
| Your code's target release | any (`--release` may be lower than 25) |
| Markdown `///` doc comments | JDK 23 or newer, which the requirement above already covers |
| Browser for reading the site | any current version |

> [!IMPORTANT]
> The processor is compiled for Java 25, so the JDK that *compiles* the documented code must be 25 or
> newer. Compiling with `--release 17` on a JDK 25 is fine.

## Modules

| Artifact | Use it for |
|----------|------------|
| `dev.doctize:doctize-annotations` | {@link Doc}, {@link DocPage} and {@link DocIgnore}. Add as `compileOnly`; the annotations have source retention and leave no trace in class files. |
| `dev.doctize:doctize-processor` | The annotation processor. Add to the annotation processor path. |
| `dev.doctize:doctize-core` | The generator as a library, see {@link Doctize}. |
| `dev.doctize:doctize-cli` | The `doctize` command line tool ([Command line](02-command-line.md)). |

## Building from source

```console
$ git clone <repository> doctize && cd doctize
$ export JAVA_HOME=/path/to/a/jdk-17-or-newer      # runs Gradle; JDK 25 is downloaded automatically
$ ./gradlew build                                   # compiles and runs all tests
$ ./gradlew publishToMavenLocal                     # makes the artifacts available to your own builds
$ ./gradlew :doctize-cli:installDist                # builds doctize-cli/build/install/doctize/bin/doctize
```

Gradle 9 itself needs a JDK 17 or newer. The project's Java 25 toolchain is provisioned by Gradle on the
first build, which needs network access.

> [!TIP]
> Put `doctize-cli/build/install/doctize/bin` on your `PATH` to run `doctize` from anywhere. Remember to
> point `JAVA_HOME` at a JDK 25.

## Annotation processing in your build

Since JDK 23, `javac` only runs annotation processors that you name explicitly. Gradle and Maven do this
for you when the processor is on the annotation processor path, as in the [Quick start](../quick-start/quick-start.md).
With any other tool, pass `-processorpath` or `-processor dev.doctize.processor.DocProcessor` to `javac`.
