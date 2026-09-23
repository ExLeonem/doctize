---
title: Template reference
description: The data every template receives.
keywords: [mustache, variables, context, views]
---

# Template reference

This is the data your [templates](03-theming.md#writing-templates) can use. The authoritative definition is the
{@link Views} class, whose nested records mirror these tables exactly; each record component is a variable.

Names ending in `Html` contain HTML: print them with `{{{triple braces}}}`. URLs are relative to the site
root: print them as `{{basePath}}{{url}}`.

## layout

The frame of every page. Its context is a `LayoutView`.

| Variable | Type | Meaning |
|----------|------|---------|
| `documentTitle` | text | The text for `<title>` |
| `title` | text | The page title |
| `description` | text | A short description of the page, may be empty |
| `basePath` | text | `""`, `"../"` ...: the way back to the site root |
| `url` | text | The URL of the page, relative to the site root |
| `home` | boolean | `true` on the home page |
| `contentHtml` | HTML | The rendered body |
| `project` | `project` | See below |
| `theme` | `theme` | See below |
| `navigation` | list of `navigation` | The sections, with their pages |
| `toc` | list | The "On this page" entries: `text`, `id`, `nested` |
| `section` | object or absent | The breadcrumb of the page's section: `title`, `url` |
| `previous`, `next` | object or absent | The neighbouring pages: `title`, `section`, `url` |
| `canonicalUrl` | text | The address of the page if the site's address is known, else empty |
| `navTabs` | list | The tabs above the navigation: `area` (`guides` or `api`), `title`, `current`; empty unless the site has both |

### project

| Variable | Meaning |
|----------|---------|
| `project.name`, `project.tagline`, `project.version` | as configured |
| `project.repository`, `project.footer` | as configured; empty if not set, so `{{#project.repository}}` works as a test |

### theme

| Variable | Meaning |
|----------|---------|
| `theme.name` | the configured theme name; empty for the built-in theme |
| `theme.stylesheets` | the stylesheets of the named theme, URLs relative to the site root, `{{.}}` inside a loop; `partials/head` loads them after `theme.css` |
| `theme.accent` | the configured accent colour |
| `theme.extraCss`, `theme.extraJs` | lists of URLs (relative to the site root), `{{.}}` inside a loop |
| `theme.options` | the values of `theme.options` in the configuration: `{{theme.options.banner}}` |

### navigation

A list of sections. Each has `id`, `title`, `url` (its first page), `current` (the page is in this section),
`area` (`api` for a section of API pages, else `guides`) and `groups`. A group has a `label` (may be empty) and
`items`; an item has `title`, `url`, `current` and `deprecated`.

## home

The context is a `HomeView`: `introHtml` (the rendered `index.md`), `project`, `startUrl` (the first section) and
`sections`, a list of cards with `title`, `description`, `url`, `pageLabel` (for example "3 pages") and `pages` (the
first few items, with `title` and `url`).

## guide

For Markdown guides and `@DocPage` pages: `title`, `descriptionHtml` (from the `description` front matter) and `bodyHtml`.

## api-type

For pages of {@link Doc} types. The context is a `TypeView`:

| Variable | Meaning |
|----------|---------|
| `title` | the page title: the `title` of the annotation, else the simple name |
| `name`, `qualifiedName`, `packageName` | the names of the type |
| `kind` | `class`, `interface`, `enum`, `record` or `annotation` |
| `signatureHtml` | the highlighted declaration, with links to documented types |
| `descriptionHtml` | the description |
| `deprecated` | absent, or an object with `html` (the reason) |
| `params` | list of type parameters or record components: `name`, `descriptionHtml` |
| `tags` | list of `label` and `html`, for `@since`, `@see`, `@apiNote` ... |
| `groups` | list of member groups: `id`, `title` and `members` |
| `sourceUrl` | the address of the declaration in the repository; empty if unknown |

A member (in `groups`, rendered by `partials/member`) has `id`, `name`, `kind`, `signatureHtml`, `summaryHtml`,
`descriptionHtml`, `deprecated`, `params` (`name`, `typeHtml`, `descriptionHtml`), `returns` (`typeHtml`,
`descriptionHtml`; absent when there is no `@return`), `throwsList` (`typeHtml`, `descriptionHtml`), `tags` and `sourceUrl`
(empty if unknown).

The groups appear in this order: enum constants, fields, constructors, methods, nested types. Groups and
members without content are absent.

## CSS hooks

The default stylesheet uses these classes, which are useful when you write your own CSS:

| Class | Element |
|-------|---------|
| `.topbar`, `.sidebar`, `.main`, `.doc`, `.toc` | the regions of the layout |
| `.prose` | rendered Markdown |
| `.code-block`, `.code-title`, `.copy-btn` | a code block, its title and the copy button |
| `.tabs`, `.tab-panel`, `.tab-btn` | code tabs |
| `.markdown-alert`, `.markdown-alert-tip` ... | alerts |
| `.signature`, `.member`, `.kind`, `.detail`, `.tag-block` | API pages |
| `.tok-k`, `.tok-s`, `.tok-c` ... | syntax highlighting: keyword, string, comment, number (`n`), type (`t`), function (`f`), annotation (`a`), property (`p`) |
