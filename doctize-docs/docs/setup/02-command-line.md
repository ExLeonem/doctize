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
doctize generate [-c <config>] [-m <model>]... [-g <dir>] [-t <dir>] [-o <dir>] [--serve [--port <n>]]
```

| Option | Meaning |
|--------|---------|
| `-c`, `--config` | The configuration file. By default `./doctize.yml` if it exists. |
| `-m`, `--model` | Where the annotation processor left its output: a compiled classes directory such as `build/classes/java/main`, a `model.json` file, or a jar. Repeat the option for several modules. |
| `-g`, `--guides` | The directory with Markdown guides. Overrides `guides` in the configuration. |
| `-t`, `--theme` | A custom theme directory. Overrides `theme.path`. |
| `-o`, `--output` | The output directory. Default `build/docs`. |
| `--serve` | Serves the result on `http://localhost:<port>/` until you press Ctrl+C. |
| `--port` | The port for `--serve`. Default `8080`. |

Options given on the command line win over the configuration file. Relative paths in the *file* are
resolved against the directory of the file; relative paths on the command line against the working directory.

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

## init

Creates a starter setup.

```console
doctize init [<directory>] [--name <project name>]
```

It writes a `doctize.yml`, a home page introduction (`docs/index.md`) and two example guides (one in "Quick start",
one in "Setup") into the directory (default: the current one). It refuses to overwrite an existing `doctize.yml`.
