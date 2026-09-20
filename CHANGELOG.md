# Changelog

All notable changes to Doctize are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Architecture diagrams as Mermaid diagrams, all of them Pro features: an overview of the packages, class diagrams, state diagrams,
  entity-relationship diagrams for databases (JPA, or your own annotations) and flow charts of call flows. Each kind is switched on and off
  and configured on its own under `architecture.diagrams` (`--diagram` on the command line, `diagrams` in Gradle). New annotations mark code:
  `@DiagramIgnore`, `@DiagramInclude`, `@StateMachine`, `@Transition` and `@Flow`; they are kept in the class file, and their `types` are text
  (`DiagramTypes`) so that libraries that use them cause no compiler warning downstream.
- Mermaid diagrams in guides: a fenced code block with the language `mermaid` is drawn in the browser. The Mermaid library (12.0.0, MIT) is
  bundled and copied into a site only if a page has a diagram, so diagrams work offline; a theme needs `diagrams.js`, which the default layout has.
- A license mechanism. A license is a signed text file (Ed25519) that Doctize checks offline, and that unlocks features. It is found
  through `--license`, `DOCTIZE_LICENSE`, `doctize.license` next to the configuration or `~/.doctize/license`. A licensed feature that
  was switched on without a usable license is skipped with a warning. New `doctize license` command; `Doctize.Builder#license`,
  `licenseKey`, `licenseVerifier`; `GenerationReport#license`; the `license` setting of the Gradle plugin.
- The overview of the architecture, the first licensed feature. It reads compiled classes, finds the dependencies between packages, and adds
  a page with a diagram, tables, dependency cycles, layers and forbidden dependencies. Cycles and broken rules are warnings.
  Configured under `architecture:` in `doctize.yml`: `enabled`, `title`, `section`, `order`, `rootPackage`, `depth`, `include`,
  `exclude`, `components`, `layers`, `forbid`, `cycles`, `external`, `libraryDepth`, `minReferences`, `direction`.
  New `--architecture` and `--classes` options, `Doctize.Builder#architecture` and `classes`, and `architecture` in the Gradle plugin.
- The guide *Architecture of Doctize* shows the packages of the generator as a diagram made from the code, and a test compares its table
  and diagram with the code, so that the guide cannot go out of date unnoticed. The docs of Doctize have a section of their own, *Architecture*, holding this guide and, in a licensed build, the diagrams of the
  code of all modules: package overview, class diagrams of the types the pipeline is made of, and flow charts of the main flows.
- Flow entry points can be named in the configuration with a title, a description and their own depth (`entryPoints` accepts a mapping as well as
  `Type#method`), so that code that carries no `@Flow` can have flows. The overview has its own `exclude`.
- The documentation of Doctize describes its own architecture: the guide *Architecture of Doctize* (modules, the packages of the generator,
  the rules they follow, and the one cycle they do not), and, when the docs are built with a license, a *Live architecture map* of the
  current code. The docs build and the publishing workflow pick the license up (`doctize-docs/doctize.license`, `DOCTIZE_LICENSE`,
  `~/.doctize/license`); without one the page is left out and the build is unchanged.
- `doctize-licensing`, the vendor's tool for creating the signing key and issuing licenses. It is not published.

- The Gradle plugin `dev.doctize` (`doctize-gradle-plugin`). In a Java project it adds the annotations and the annotation
  processor, and the `generateDocs` task, which compiles the code and runs the command line tool on a Java 25 toolchain.
  A `doctize { }` block, and the task options `--github-pages`, `--base-url`, `--custom-domain` and `--fail-on-warnings`,
  configure it; `-PdoctizeArgs` still works. Several modules are documented as one site with `modules(...)`.
  The plugin runs on Gradle 9 with a JDK 17 or newer.
- The version check of the documentation also covers plugin declarations (`id("dev.doctize") version "..."`).

### Changed

- The guide *Running from Gradle* describes the plugin; the `JavaExec` task it used to show remains as an alternative.

## [0.1.0]

The first release.

### Added

- Annotations `@Doc`, `@DocPage` and `@DocIgnore`, and an annotation processor that extracts the Javadoc of annotated
  types at compile time. Classic `/** */` comments and Markdown `///` comments (JEP 467) are supported.
- A generator that builds a static documentation site from the extracted model and a directory of Markdown guides:
  sections declared in `doctize.yml`, by folder, by front matter or in code; API reference pages with signatures,
  parameters, exceptions, tags and cross-references; GitHub-flavoured Markdown with alerts and code tabs;
  build-time syntax highlighting.
- A default theme with light and dark mode, a responsive layout, and client-side full-text search that is typo
  tolerant, camelCase aware and works offline. Themes are layered: a custom theme replaces single files of the built-in one.
- The `doctize` command line tool with `generate` (including `--serve` and `--fail-on-warnings`) and `init`.
- GitHub Pages support: `.nojekyll`, `CNAME`, a `404.html` that works at any depth, `sitemap.xml`, `robots.txt`
  and canonical links, plus a workflow scaffold (`doctize init --github-pages`).
- Warnings for `{@link}` tags that point to nothing.
- Unit, integration and system tests, and a documentation site generated by Doctize itself.

[Unreleased]: https://github.com/ExLeonem/doctize/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ExLeonem/doctize/releases/tag/v0.1.0
