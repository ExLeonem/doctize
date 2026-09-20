---
title: Licensing
description: Which features need a license, and how to install one.
keywords: [license, key, unlock, features, DOCTIZE_LICENSE, expiry]
---

# Licensing

Doctize is free to use: annotations, API reference, guides, theming, search, Mermaid diagrams that you write by hand, and publishing all
work without a license. The **Pro features** need a **Doctize Pro license**, which you buy:

| Feature | What it does | Guide |
|---------|--------------|-------|
| `architecture` | Draws the architecture of a project from its compiled code: the overview of packages, class diagrams, state diagrams, entity-relationship diagrams and flow charts, with cycle and rule checks. | [Architecture diagrams](../guides/08-architecture-diagrams.md) |

Pro licenses are sold by the maintainers of Doctize; see the project's repository for how to get one. A license is a small text file. It
names who it was issued to, which features it unlocks and, optionally, the day it expires.
Doctize checks it on your machine, without a network connection: nothing about you, your project or your license is ever
sent anywhere.

## Install a license

Doctize looks for a license in these places, and uses the first it finds:

1. the license you name explicitly: `--license <file>` on the [command line](02-command-line.md), `license = file(...)` in the
   [Gradle plugin](03-gradle.md), or {@link Doctize.Builder#license(java.nio.file.Path)} in code,
2. the environment variable `DOCTIZE_LICENSE`, which holds the license itself, not a file name. This is the way for CI: store the
   license as a secret and expose it as this variable,
3. the file `doctize.license` next to `doctize.yml` (in a Gradle project: in the project directory),
4. the file `.doctize/license` in your home directory, for a license that should work for every project on your machine.

```console
$ cp ~/Downloads/acme.license doctize.license
$ doctize license
License from the file /work/acme-cache/doctize.license
Licensed to Acme Corp, valid until 2027-12-31: architecture
  architecture: unlocked
```

`doctize license` shows what Doctize found and what it unlocks. It exits with `1` if there is no usable license, so it can also be a
step of your build.

> [!TIP]
> Whether to commit `doctize.license` depends on who may use it. A license issued to your organisation should not be in a public
> repository. Keep it out of version control and pass it to CI as `DOCTIZE_LICENSE`.

A license file may contain comment lines that start with `#`. Keep the license itself on one line, exactly as it was issued.

## What happens without a license

Switching on a licensed feature without a usable license never fails the build. Doctize generates everything else, skips the
feature, and says why:

```console
$ doctize generate --architecture
Generated 12 pages in 4 sections → build/docs
doctize: warning: The architecture diagrams were skipped. The architecture diagrams need a Doctize Pro license, and none was found. Pass one
with --license, set DOCTIZE_LICENSE, or put doctize.license next to doctize.yml.
```

The same happens when the license has expired, was changed after it was issued, was issued for other features, or is no license
at all; the warning names which of these it is and where the license came from. A warning is a warning like any other, so
`--fail-on-warnings` turns a missing license into a failing build, which is what you want in a pipeline that is supposed to publish
the map.

A license is valid through its last day, in UTC. Features you are not licensed for are never switched on by accident:
`architecture.enabled` is `false` until you set it.

Doctize itself is free software under the Apache License 2.0, and Pro is a commercial license for the Pro features: it gives you the right
to use them, and is checked in the code by the signature of the license, not by a server.

## For developers: checking licenses in code

{@link Doctize} reports the license it used in {@link GenerationReport}. The pieces are public, if you want to check a license
yourself:

- {@link License} is what a license grants, and {@link Feature} the features that exist,
- {@link LicenseVerifier} checks a license: the signature against the public key that ships with Doctize, and the expiry,
- {@link LicenseLocator} finds it by the rules above, and {@link LicenseStatus} answers whether a feature may be used, and if
  not, why,
- {@link LicenseException} is thrown for a license that cannot be read.

A license is `<content>.<signature>` on one line: the content is JSON, the signature an Ed25519 signature of exactly those bytes.
Doctize only contains the public key, so it can check licenses but never issue them.
