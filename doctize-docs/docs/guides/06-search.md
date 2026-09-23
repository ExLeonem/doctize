---
title: Search
description: What the search finds, and how to use it.
keywords: [find, shortcut, index, typo]
---

# Search

Every page has a search dialog. Open it with <kbd>Ctrl</kbd>+<kbd>K</kbd> (<kbd>⌘</kbd>+<kbd>K</kbd> on macOS),
with <kbd>/</kbd>, or with the button in the top bar. <kbd>↑</kbd> <kbd>↓</kbd> move through the results,
<kbd>↵</kbd> opens one, <kbd>Esc</kbd> closes the dialog.

## Results are tagged

Every result says what it found, so you can tell a class from a method of the same name at a glance:

| Tag | Found |
|-----|-------|
| `class`, `interface`, `enum`, `record`, `annotation` | an API page |
| `method`, `constructor` | a method or constructor on an API page |
| `field`, `enum constant` | a field or enum constant on an API page |
| `page`, `section` | a guide, or a heading in it |

## Search only the API or the guides

When a site has both API pages and guides, a selector next to the search field limits the search to the
**API reference** or the **Guides**; **Everywhere** searches both. The choice is remembered in the browser.

API pages are found by the names of types and members, and by their keywords. Their descriptions are not searched,
so a search for a method does not turn up every member that mentions it, and results from the API show no text.

## What is searched

The text is split at headings, so a result leads to the **section** that matches, not just to the page. For API
pages every member is a section of its own, which is why a search for `evict` takes you straight to
`evict()`. Hits on one page are grouped below its name, at most three per page.

| Field | Weight | Contains |
|-------|--------|----------|
| Page title | highest | The title of the page |
| Heading | high | The heading of the section, or the name of the member |
| Keywords | high | The `keywords` of a page or a `@Doc` type |
| Text | normal | The text of guides; API pages have none |

## How matching works

- **Prefixes.** `instal` finds "installation" while you type.
- **Identifiers.** `CacheBuilder` is indexed as `cachebuilder`, `cache` and `builder`, so `builder` finds it. The
  same goes for `snake_case`.
- **Typos.** When a word matches nothing, Doctize looks for words that are one edit away (two for long words):
  `evicton polcy` finds the eviction policy.
- **All words.** A result must contain all the words. If nothing does, results with some of the words are shown.
- **Exact phrases.** A heading or title that equals or contains the whole query ranks above others.

## Offline, from a file

The index is a script (`search-index.js`) that defines a JavaScript variable, not a JSON file that is fetched.
Browsers block `fetch` on pages opened from disk, but not scripts, so search also works when you open
`index.html` straight from the file system or serve the site from any static host.

Every record of the index has a `kind` (the tag of the result) and an `area`, `api` or `guides`. The search dialog
uses them, and `DoctizeSearch.search(index, query, { scope: "api" })` searches one area only.

## Size

The index contains all the text of your site, and the browser builds its search structures from it when the
page loads, while idle. This is instant for hundreds of pages. A site with many thousands of pages would need
an index that is built ahead of time.
