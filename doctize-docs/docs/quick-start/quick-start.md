---
title: Quick start
order: 1
description: Document a library and generate a site in ten minutes.
keywords: [tutorial, getting started, hello world]
---

# Quick start

By the end of this page you will have a documentation site for a small library, with an API reference
generated from your Javadoc and a guide you wrote yourself.

> [!WARNING]
> Doctize is not published to a public repository yet. Until it is, install it locally with
> `./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.
> See [Installation](../setup/01-installation.md) for the requirements.

## 1. Add the annotations and the processor

The annotations are only needed while your code compiles, and the processor extracts your Javadoc.
With Gradle, the plugin adds both, and the task that generates the site in step 4 as well
(see [Running from Gradle](../setup/03-gradle.md) for the plugin repository you need in `settings.gradle.kts`):

```kotlin tab="Gradle plugin (Kotlin)"
plugins {
    `java-library`
    id("dev.doctize") version "0.1.0"
}
```

```groovy tab="Gradle plugin (Groovy)"
plugins {
    id 'java-library'
    id 'dev.doctize' version '0.1.0'
}
```

```kotlin tab="Gradle (Kotlin)"
dependencies {
    compileOnly("dev.doctize:doctize-annotations:0.1.0")
    annotationProcessor("dev.doctize:doctize-processor:0.1.0")
}
```

```groovy tab="Gradle (Groovy)"
dependencies {
    compileOnly 'dev.doctize:doctize-annotations:0.1.0'
    annotationProcessor 'dev.doctize:doctize-processor:0.1.0'
}
```

```xml tab="Maven"
<dependency>
  <groupId>dev.doctize</groupId>
  <artifactId>doctize-annotations</artifactId>
  <version>0.1.0</version>
  <scope>provided</scope>
</dependency>

<!-- in maven-compiler-plugin: -->
<annotationProcessorPaths>
  <path>
    <groupId>dev.doctize</groupId>
    <artifactId>doctize-processor</artifactId>
    <version>0.1.0</version>
  </path>
</annotationProcessorPaths>
```

## 2. Annotate a type

Put {@link Doc} on the types that belong in the reference, and write their documentation as Javadoc.
Both comment styles work; this one uses Markdown (`///`):

```java title="Greeter.java"
import dev.doctize.annotations.Doc;

/// Greets people in their own language.
///
/// > [!TIP]
/// > Use {@link #greet(String, String)} when you know the language.
///
/// @since 1.0
@Doc(order = 1)
public class Greeter {

    /// Greets someone in English.
    ///
    /// @param name the name of the person, never `null`
    /// @return the greeting
    public String greet(String name) {
        return greet(name, "en");
    }

    /// Greets someone in the given language.
    ///
    /// @param name the name of the person
    /// @param language an ISO 639-1 code such as `en` or `de`
    /// @return the greeting
    /// @throws IllegalArgumentException if the language is not supported
    public String greet(String name, String language) { /* ... */ }
}
```

When you compile, the processor writes `META-INF/doctize/model.json` into the class output.

## 3. Create the site structure

`doctize init` creates a `doctize.yml` and a few starter guides:

```console
$ doctize init --name "My Library"
created doctize.yml
created docs/index.md
created docs/quick-start/01-first-steps.md
created docs/setup/installation.md
Next: annotate types with @Doc, then run `doctize generate`.
```

Every folder in `docs/` is a section of the site. Add your own by creating a folder, or declare it in
`doctize.yml` to control its position. See [Structuring the site](../guides/02-structuring-the-site.md).

## 4. Generate and look at it

```console
$ doctize generate --model build/classes/java/main --output build/docs --serve
Generated 3 pages in 3 sections → build/docs
Serving at http://localhost:8080/ (Ctrl+C to stop)
```

Open <http://localhost:8080/>. Press <kbd>/</kbd> and search for "greet": the result leads directly to the
`greet()` method of the `Greeter` page.

With the Gradle plugin, run `./gradlew generateDocs` instead. It compiles your code and writes the site to `build/docs`; open
`build/docs/index.html`, which works straight from the file system. The plugin does not serve the site and does not scaffold
it: create `doctize.yml` and `docs/` with `doctize init` as above, or by hand.

## Where to go next

- [Documenting your code](../guides/01-writing-documentation.md): everything you can write in a comment.
- [Structuring the site](../guides/02-structuring-the-site.md): sections, ordering, linking.
- [Theming](../guides/03-theming.md): make it look like your project.
- The {@link Doc} reference lists every attribute of the annotation.
