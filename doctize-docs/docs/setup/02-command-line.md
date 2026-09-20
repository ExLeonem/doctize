---
title: Command line
description: The generate and init commands.
---

# Command line

```console
$ doctize --help
$ doctize generate --help
```

## generate

Builds the site.

```console
doctize generate [-c <config>] [-m <model>]... [--classes <path>]... [-g <dir>] [-t <dir>] [-o <dir>]
                 [--architecture] [--diagram <kind>]... [--license <file>]
                 [--github-pages] [--base-url <url>] [--cname <domain>] [--fail-on-warnings] [--serve [--port <n>]]
```

| Option | Meaning |
|--------|---------|
| `-c`, `--config` | The configuration file. By default `./doctize.yml` if it exists. |
| `-m`, `--model` | Where the annotation processor left its output: a compiled classes directory such as `build/classes/java/main`, a `model.json` file, or a jar. Repeat the option for several modules. |
| `--classes` | Compiled classes (a directory or a jar) for the [architecture diagrams](../guides/08-architecture-diagrams.md) to analyze, in addition to the `--model` locations. Repeat the option for several. Unlike `--model`, they need no documentation model. |
| `--architecture` | Generates the architecture diagrams, a Pro feature. Same as `architecture.enabled: true`. |
| `--diagram` | Switches on a kind of architecture diagram: `overview`, `classes`, `states`, `entities` or `flows`, in any case. Repeat it for several. Implies `--architecture`. |
| `--license` | The [license](06-licensing.md) file. By default Doctize looks at `DOCTIZE_LICENSE`, `doctize.license` next to the configuration, and `~/.doctize/license`. |
| `-g`, `--guides` | The directory with Markdown guides. Overrides `guides` in the configuration. |
| `-t`, `--theme` | A custom theme directory. Overrides `theme.path`. |
| `-o`, `--output` | The output directory. Default `build/docs`. |
| `--github-pages` | Prepares the output for GitHub Pages: writes `.nojekyll`, and `CNAME` if a domain is set. See [Publishing to GitHub Pages](05-github-pages.md). |
| `--base-url` | The address the site will be published at, e.g. `https://acme.github.io/cache/`. Enables `404.html`, `sitemap.xml`, `robots.txt` and canonical links. |
| `--cname` | The custom domain of a GitHub Pages site, e.g. `docs.acme.dev`. |
| `--fail-on-warnings` | Exits with code `1` if generation produced any warning. Use it in CI so that a broken `{@link}` fails the build. |
| `--serve` | Serves the result on `http://localhost:<port>/` until you press Ctrl+C. |
| `--port` | The port for `--serve`. Default `8080`. |

Options given on the command line win over the configuration file. Relative paths in the *file* are
resolved against the directory of the file; relative paths on the command line against the working directory.

### Warnings

Problems that do not stop the site from being generated are printed to standard error as `doctize: warning: ...` and
do not change the exit code, unless you pass `--fail-on-warnings`. Doctize warns about `{@link}` tags that point to no documented type or member (links to
JDK classes are not checked), and about GitHub Pages options that lack an address.

### The output directory is safe

Doctize removes the output of a previous run before it writes a new one, so pages you deleted disappear.
It does this **only** in a directory it created itself, recognised by a `.doctize-site` marker file. If you
point `--output` at a directory with other files in it, those files are left alone.

### Exit codes

| Code | Meaning |
|------|---------|
| `0` | The site was generated. |
| `1` | Doctize could not generate the site: a missing model, invalid configuration, invalid front matter ... The message names the file and the problem. |
| `2` | Invalid command line usage. |

## license

Shows which [license](06-licensing.md) Doctize uses and what it unlocks.

```console
doctize license [-c <config>] [--license <file>]
```

```console
$ doctize license
License from the file /work/acme-cache/doctize.license
Licensed to Acme Corp, valid until 2027-12-31: architecture
  architecture: unlocked
```

The exit code is `0` for a valid license, and `1` if there is none or it cannot be used, with the reason. `-c` names the
configuration file next to which `doctize.license` is looked for.

## init

Creates a starter setup.

```console
doctize init [<directory>] [--name <project name>] [--github-pages]
```

It writes a `doctize.yml`, a home page introduction (`docs/index.md`) and two example guides (one in "Quick start",
one in "Setup") into the directory (default: the current one). It refuses to overwrite an existing `doctize.yml`.

With `--github-pages` it also adds a `hosting` block to `doctize.yml` and a GitHub Actions workflow,
`.github/workflows/docs.yml`, that publishes the site; see [Publishing to GitHub Pages](05-github-pages.md).
