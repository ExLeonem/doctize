---
title: Architecture map
description: Map out the packages of a project and how they depend on each other. A licensed feature.
keywords: [architecture, dependencies, packages, layers, cycles, diagram, components, rules, license]
---

# Architecture map

The architecture map answers a question that the API reference cannot: *how is this project put together?* Doctize reads the
compiled classes, works out which package depends on which, and adds one page to the site with

- a **map**: a box per part of the project, an arrow per dependency, coloured to follow light and dark mode,
- a table of the **parts**, what each one depends on and what depends on it,
- **dependency cycles**, where parts cannot be understood or changed one without the other,
- **layers** and **forbidden dependencies**, the rules you declare, and the places that break them,
- optionally the **libraries** the code uses.

> [!IMPORTANT]
> The architecture map is a licensed feature, see [Licensing](../setup/06-licensing.md). Without a license, Doctize skips the
> page and prints a warning; everything else is generated as usual.

## Switch it on

The map is off until you ask for it, in `doctize.yml`:

```yaml title="doctize.yml"
architecture:
  enabled: true
```

or on the command line, with `--architecture`, or `architecture = true` in the [Gradle plugin](../setup/03-gradle.md). Doctize
analyzes the class directories and jars you already give it with `--model`. Give more with `--classes`, for example for modules that
have nothing to document, and therefore no model:

```console
$ doctize generate --architecture --classes core/build/classes/java/main --classes client/build/classes/java/main
```

The Gradle plugin passes the classes of the project for you. The page appears in the section *Architecture*, after the others.

## How the map is made

Doctize reads the class files, not the source, so it sees what the compiler saw. A class **depends on** another if its class file
mentions it: as super type, in a field, parameter, return type or generic type argument, in a `throws` clause, in an annotation that
is kept in the class file, or in the code of a method, by creating it, calling it, casting to it or comparing against it.

- **Parts.** By default a part is a package. Packages can be merged with [`depth`](#depth) and gathered into named
  [`components`](#components).
- **Types.** Nested and anonymous classes count as part of the class they are in. Only what is in the analyzed classes is a part.
- **Weight.** The number of *pairs of types* in which a type of one part refers to a type of the other. A thicker arrow on the map is
  a larger weight; hover over an arrow to see it.
- **Direction.** An arrow points from a part to the parts it depends on. Parts that depend on others are drawn above them (or,
  with `direction: left-right`, to the left), so a healthy architecture reads from top to bottom.
- **The JDK** is not a dependency. Other libraries are only listed if you ask, see [`external`](#external).

### What it cannot see

Class files do not say everything. Dependencies that exist only at run time are invisible: reflection, class names in strings,
service loaders and wiring done by a framework. Constants of another class are copied into the code by the compiler, and annotations with
source retention leave no trace. So the map shows what the code *requires to compile and link*, which is what
determines how the parts can be built, tested and released.

## Options

All options are optional and set under `architecture:` in `doctize.yml`; they map onto {@link ArchitectureSettings}. Unknown
keys are rejected, like everywhere in the [configuration](../setup/04-configuration.md).

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | `false` | Generate the map. Same as `--architecture`. |
| `title` | `Architecture` | The title of the page. |
| `section` | `Architecture` | The section the page is in, by id or title. It is created if you did not declare it. |
| `order` | `100` | Where the page is in its section. |
| [`rootPackage`](#root-package) | the package all packages share | Where the map starts. Only this package and the ones below it are shown, named relative to it. |
| [`depth`](#depth) | `0` | How many package levels below the root are shown separately; deeper packages are merged. `0` shows all. |
| [`include`](#include-and-exclude) | all | Only packages that match one of these patterns are shown. |
| [`exclude`](#include-and-exclude) | none | Packages that match one of these patterns are left out. |
| [`components`](#components) | none | Named groups of packages, drawn as one box each. |
| [`layers`](#layers) | none | Parts or packages from the top of the architecture to the bottom. Dependencies pointing upwards are reported. |
| [`forbid`](#forbid) | none | Dependencies that must not exist. Each one that does is reported. |
| `cycles` | `true` | Find dependency cycles, mark them red on the map and report them. |
| [`external`](#external) | `false` | List the libraries the code uses. |
| `libraryDepth` | `2` | How many package levels identify a library, see [`external`](#external). |
| `minReferences` | `1` | The least weight a dependency needs to be drawn on the map. The tables always list all. |
| `direction` | `top-down` | `top-down` or `left-right`. |

### Package patterns

`include`, `exclude`, `components` and the rules take **package patterns**. A pattern is a package name where a segment can be

- exact: `com.acme.api` is that package only,
- `*` for any characters within a segment: `com.acme.*` is every direct sub-package of `com.acme`, `com.acme.*Impl` those ending in `Impl`,
- `**` for any number of segments, none included: `com.acme.api.**` is `com.acme.api` and everything below it, and `**.internal`
  every package called `internal`.

Patterns always match the full package name.

### Root package

Names on the map are relative to the root package, so `com.acme.billing.invoice` becomes `billing.invoice`. Doctize uses
the longest package name that all packages share. Set `rootPackage` to start deeper, and to leave everything outside it out:

```yaml
architecture:
  rootPackage: com.acme.billing
```

### Depth

A large project has too many packages for a map. With `depth`, packages deeper than that many levels below the root are merged into
their ancestor at that level:

```yaml
architecture:
  rootPackage: com.acme
  depth: 1        # com.acme.billing.invoice and com.acme.billing.tax are shown as "billing"
```

### Include and exclude

```yaml
architecture:
  include: ["com.acme.**"]
  exclude: ["**.generated", "**.internal", "com.acme.testing.**"]
```

`exclude` wins over `include`. Excluded classes are left out of the map, but Doctize still knows they belong to the project, so a
dependency on them is not listed as a library.

Excluding one exact package is often the fix for a noisy map. Many projects have a facade or shared exception in their root package
that everything refers to and that refers to everything: a cycle through the root that hides the real structure. Doctize's own map
excludes its root package this way, and shows its real cycles:

```yaml
architecture:
  exclude: ["dev.doctize"]   # exactly this package, not the ones below it
```

### Components

A component gathers packages that belong together under one name, and one box:

```yaml
architecture:
  components:
    - name: Web
      packages: ["com.acme.web.**", "com.acme.api.**"]
      description: HTTP endpoints and their DTOs.
    - name: Business
      packages: [com.acme.domain, com.acme.service]
      description: The rules of the business.
    - name: Persistence
      packages: ["com.acme.jdbc.**"]
```

A package belongs to the first component that matches it, and packages that match none are shown on their own. Descriptions get
their own column in the table of parts. Component names are what [`layers`](#layers) and [`forbid`](#forbid) can refer to.

### Layers

Declare the layers of your architecture from the top to the bottom. A part may depend on parts in its own layer and below; a
dependency that points upwards is a violation:

```yaml
architecture:
  components:
    - {name: Web, packages: ["com.acme.web.**"]}
    - {name: Business, packages: ["com.acme.domain.**", "com.acme.service.**"]}
    - {name: Persistence, packages: ["com.acme.jdbc.**"]}
  layers: [Web, Business, Persistence]
```

An entry is the name of a part, a component or a package as shown on the map, or a package pattern. The layers also decide the
rows of the map. Parts in no layer are unconstrained, and are drawn below the layers.

### Forbid

Where layers are too coarse, forbid single dependencies. `from` and `to` are package patterns or names of components:

```yaml
architecture:
  forbid:
    - from: com.acme.domain.**
      to: com.acme.jdbc.**
      reason: The domain must not know how it is stored.
    - from: Web
      to: Persistence
```

Every package pair that breaks a rule is reported with an example type pair and the reason.

### External

`external: true` adds a table of the libraries the code uses, which parts use them, and how often. A library is identified by the
first `libraryDepth` segments of its packages, `org.commonmark` with the default of 2. Use `3` to tell apart libraries that share
the first two, such as `com.fasterxml.jackson` from other `com.fasterxml` libraries.

## Cycles and rules in the build

Cycles and broken rules are **warnings**, one per cycle and per violation:

```console
doctize: warning: Architecture: dependency cycle between config, hosting, site.
doctize: warning: Architecture: com.acme.domain must not depend on com.acme.jdbc (e.g. Order → Database): The domain must not know how it is stored.
```

With `--fail-on-warnings` (`failOnWarnings = true` in Gradle) they fail the build. That makes the map a guard: architecture rules that
are checked on every build, and documented on the site they protect. If you only want to look at cycles, `cycles: false` turns the
check off; the rules you declare are always checked.

## Reading the map

- A **red border** marks a part in a dependency cycle, and a **red arrow** an arrow inside a cycle or one that breaks a rule.
- **Parts with an accent border** are components.
- Below a part's name: the number of types in it, and the number of packages if it is a merge of several.
- Hover over a part to see its packages, and over an arrow for the two ends and the weight.
- The tables below the map say the same in words, and are found by the site search.

A map with many parts gets wide, and scrolls sideways. Reduce it with `depth`, `include`, `exclude` and `minReferences`, or
draw one map per area: `rootPackage` chooses the area.

## Doing it in code

```java
Doctize.builder()
        .configFile(Path.of("doctize.yml"))
        .model(Path.of("build/classes/java/main"))
        .classes(Path.of("other-module/build/classes/java/main"))
        .architecture()
        .license(Path.of("doctize.license"))
        .output(Path.of("build/docs"))
        .build()
        .generate();
```

{@link ArchitecturePageSource} is the page source behind it, and {@link ArchitectureAnalyzer} the analysis, which returns an
{@link ArchitectureModel} you can use for something else, such as a test that fails when a rule is broken.
