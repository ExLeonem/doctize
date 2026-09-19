---
title: Extending Doctize
description: Add pages from any source, and drive the generator from code.
keywords: [page source, api, plugin, changelog, programmatic]
---

# Extending Doctize

Doctize builds a site from **page sources**. Two are built in: the Java elements the processor extracted, and
the directory of Markdown guides. A third one is yours to write.

## A page source

A {@link PageSource} returns {@link Page}s. Everything else, placing them in a section, ordering them, linking
to them, rendering them and indexing them for search, is done for you.

This one turns a `CHANGELOG.md` into a page. The file is split into one heading per release, so each release
is a search result of its own:

```java title="ChangelogPageSource.java"
import dev.doctize.site.Page;
import dev.doctize.site.PageContent;
import dev.doctize.site.PageSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ChangelogPageSource implements PageSource {

    private final Path file;

    public ChangelogPageSource(Path file) {
        this.file = file;
    }

    @Override
    public List<Page> pages() {
        try {
            String markdown = Files.readString(file);
            return List.of(new Page(
                    "changelog",                 // slug: the file name of the page
                    "Changelog",                 // title
                    "Project",                   // section: an id or a title, created if it does not exist
                    "",                          // group label in the navigation
                    100,                         // order
                    "What changed in each release.",
                    List.of("release notes"),    // extra search keywords
                    new PageContent.Guide(markdown, "")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

Register it with the builder:

```java
Doctize.builder()
        .configFile(Path.of("doctize.yml"))
        .model(Path.of("build/classes/java/main"))
        .pageSource(new ChangelogPageSource(Path.of("CHANGELOG.md")))
        .build()
        .generate();
```

`PageSource` has one method, so a lambda works for simple cases:

```java
.pageSource(() -> List.of(page))
```

### What a page can contain

{@link PageContent} is one of:

| Content | Meaning |
|---------|---------|
| `PageContent.Guide(markdown, sourcePath)` | Markdown, which may use Javadoc inline tags such as `{@link}`. `sourcePath` is the path of the file relative to the guides directory, used to resolve relative links; pass `""` if the text does not come from a file. |
| `PageContent.Type(element)` | The reference of a type, given as a {@link SourceElement}. This is what the annotation processor produces. |

## The generator as a library

The command line tool is a thin layer over {@link Doctize}. Use the library when you want to run the
generator from a test, a build script or your own tool:

```java
GenerationReport report = Doctize.builder()
        .section("Quick start")          // structure in code; the config file's sections are then ignored
        .section("Reference")
        .guides(Path.of("docs"))
        .theme(Path.of("my-theme"))
        .model(Path.of("build/classes/java/main"))
        .model(Path.of("other-module/build/classes/java/main"))
        .output(Path.of("build/site"))
        .build()
        .generate();
```

Settings given to the builder win over the configuration file. A useful check for your build: generate the site
in a test and assert that the pages you expect exist.

## Producing the model yourself

The annotation processor is one way to fill the site. Its output is a JSON file, described by
{@link SourceModel}, which the generator reads with `ModelLocator`. If you document a language other than Java, or
have the information from somewhere else, write a `model.json` yourself: a `SourceModel` holds a list of
{@link SourceElement}s, each with a {@link Declaration}, a raw comment and a {@link Placement}. Pass the file with
`--model`. The format is versioned (`formatVersion`); newer files are rejected by older generators, and
unknown fields are ignored.
