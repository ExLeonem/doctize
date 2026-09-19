---
title: Structuring the site
description: Sections, their order, pages, links and assets.
keywords: [sections, navigation, folders, front matter, order]
---

# Structuring the site

A site is a list of **sections**; a section is a group of pages in the navigation. "Quick start", "Setup",
"Guides" and "API Reference" on this site are sections. You can shape the list in four ways, and they combine freely.

## 1. In doctize.yml

The order of `sections` is the order in the navigation:

```yaml title="doctize.yml"
sections:
  - id: quick-start
    title: Quick start
    description: Add the dependency and cache your first value.
  - Setup                     # a plain title is enough
  - id: api
    title: API Reference
```

The `description` is the teaser on the home page cards. A section that no page belongs to is not shown.

## 2. With folders

Every sub-directory of the guides directory is a section, and the Markdown files in it are its pages:

```text
docs/
  index.md                   the introduction on the home page
  quick-start/
    quick-start.md           Quick start → Quick start
  setup/
    01-installation.md       Setup → Installation
    02-command-line.md       Setup → Command line
  recipes/                   not declared in doctize.yml: created automatically
    caching-http-responses.md
  images/
    pipeline.svg             any other file is published as an asset
```

- **Ordering by file name.** A numeric prefix (`01-`, `02-`) sorts files and folders and is removed from names and URLs.
- **Undeclared sections are created on demand.** A folder that `doctize.yml` does not list still creates a section,
  appended after the declared ones, with its name capitalised. Declare it to move it or give it a description.
- **Files in the root** of the guides directory go to `defaultSection` (`Guides`), except `index.md`, which is
  the home page introduction and not a page.

## 3. With front matter

A YAML block at the top of a Markdown file overrides everything the folder and file name imply:

```markdown
---
title: Installation          # default: the first "# heading", else the file name
section: setup               # an id or a title; default: the folder
order: 10                    # default: the numeric prefix, else 100
group: How-to                # a label that groups pages in the navigation
slug: install                # the file name of the page
description: One line for cards and search results.
keywords: [gradle, maven]    # extra search terms
---
```

If the file starts with a `# Heading`, that heading is the page title and is removed from the body, because
the page template prints the title itself.

## 4. In Java code

The placement attributes of {@link Doc} and {@link DocPage} place generated pages, and the builder declares
sections in code:

```java
Doctize.builder()
        .section("Quick start")
        .section("Setup")
        .section("Reference")
        .build();
```

Once a section is declared in code, the list from the configuration file is ignored. See {@link Doctize.Builder#section(String)}.

## How pages are ordered

1. **Pages that share a `group` stay together** and get a small group label in the navigation. API pages, which are
   grouped by package unless you say otherwise, therefore appear neatly in packages. Pages without a group form
   one group of their own, without a label.
2. **Groups are ordered by the lowest `order` of their pages**, ties by name. To put a group first, give one of its
   pages a low `order`.
3. **Inside a group**, pages are sorted by `order`, then by title (ignoring case). The default `order` is 100.
4. The previous/next links at the bottom of a page follow the reading order: section by section.

## URLs

| Kind of page | URL |
|--------------|-----|
| Guide, prose page | `<section id>/<slug>.html` |
| API page | `<section id>/<qualified type name>.html`, for example `api/dev.doctize.Doctize.html` |
| Two pages with the same slug | the second gets `-2`; comparison ignores case, so the site works on case-insensitive file systems |

Slugs that are already safe in a URL keep their capitalisation; all others are lower-cased and hyphenated.

## Linking

Inside Markdown guides, ordinary relative links just work:

| You write | It points to |
|-----------|--------------|
| `[Setup](../setup/01-installation.md)` | the page generated from that file, wherever it ends up |
| `[Policies](../guides/eviction.html)` | a generated page, by its URL (for pages that have no Markdown file, such as `@DocPage`) |
| `![Diagram](../images/pipeline.svg)` | the file, copied to `assets/content/images/pipeline.svg` |
| `[Doctize](https://example.com)` | an external site, marked with an arrow |
| `{@link Doctize#builder()}` | the API reference, as in Javadoc |

All links are relative, so the site works from any sub-path and straight from the file system.

## Structure on the home page

The home page shows the name and tagline from the configuration, the text of `index.md`, and one card per
section listing its first pages.
