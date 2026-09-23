---
title: Configuration
description: Every setting of doctize.yml.
keywords: [yaml, settings, options]
---

# Configuration

The configuration file is optional, and so is every setting in it. Unknown keys are rejected, so a typo
such as `projekt:` is reported instead of silently ignored.

```yaml title="doctize.yml"
project:
  name: Acme Cache
  tagline: A small, fast, bounded in-memory cache for Java.
  version: 1.2.0
  repository: https://github.com/acme/acme-cache
  footer: © 2026 Acme Corp.

theme:
  path: my-theme
  accent: "#0d9488"
  extraCss: [brand.css]
  extraJs: [brand.js]
  options:
    banner: "We are hiring"

guides: docs
defaultSection: Guides

sections:
  - id: quick-start
    title: Quick start
    description: Add the dependency and cache your first value.
  - Setup
  - id: api
    title: API Reference

api:
  section: API Reference
  includeUndocumented: true

hosting:
  githubPages: true
  baseUrl: https://acme.github.io/acme-cache/
  customDomain: docs.acme.dev
```

Relative paths (`theme.path`, `extraCss`, `extraJs`, `guides`) are resolved against the directory that
contains the file. The settings map onto {@link DoctizeConfig}.

## project

Shown in the header, on the home page and in the footer. See {@link ProjectInfo}.

| Key | Default | Meaning |
|-----|---------|---------|
| `name` | `Documentation` | The name in the top bar, the home page and the browser title. |
| `tagline` | empty | One line below the name on the home page. |
| `version` | empty | Shown as a badge next to the name. |
| `repository` | empty | Adds a repository icon that links here to the top bar. |
| `footer` | empty | A line of text in the footer of every page. |

## theme

See {@link ThemeSettings} and [Theming](../guides/03-theming.md).

| Key | Default | Meaning |
|-----|---------|---------|
| `path` | none | A directory with a custom theme, layered over the built-in theme. |
| `accent` | theme default | A CSS colour for links and highlights. |
| `extraCss` | none | Stylesheets added after the theme's own. Copied to `assets/custom/`. |
| `extraJs` | none | Scripts added at the end of every page. Copied to `assets/custom/`. |
| `options` | none | Free-form text values your templates can read as `{{theme.options.<key>}}`. |

## guides and defaultSection

| Key | Default | Meaning |
|-----|---------|---------|
| `guides` | none | The directory with Markdown files, see [Structuring the site](../guides/02-structuring-the-site.md). |
| `defaultSection` | `Guides` | The section for Markdown files in the root of that directory that do not name one. |

## sections

The list of sections in navigation order, see {@link SectionSpec}. An entry is either a plain title or a
mapping:

| Key | Default | Meaning |
|-----|---------|---------|
| `id` | the slug of `title` | Used in URLs (`<id>/page.html`) and to refer to the section from pages. |
| `title` | the `id`, capitalised | The name in the navigation. |
| `description` | empty | The teaser on the home page. |

## api

See {@link ApiSettings}.

| Key | Default | Meaning |
|-----|---------|---------|
| `section` | `API Reference` | The section for `@Doc` types that do not name one. |
| `includeUndocumented` | `true` | Whether members without a doc comment are listed. |

## hosting

Where and how the site is published; see [Publishing to GitHub Pages](05-github-pages.md) and {@link HostingSettings}.

| Key | Default | Meaning |
|-----|---------|---------|
| `baseUrl` | derived, see below | The address the site is published at, e.g. `https://acme.github.io/acme-cache/`. Enables `404.html`, `sitemap.xml`, `robots.txt` and canonical links. |
| `githubPages` | `false` | Prepares the output for GitHub Pages: writes `.nojekyll` (and `CNAME` for a custom domain). |
| `customDomain` | none | A host name such as `docs.acme.dev` for a GitHub Pages site. |

If `baseUrl` is not set, the address is `https://<customDomain>/`, or, with `githubPages`, derived from `project.repository`.
