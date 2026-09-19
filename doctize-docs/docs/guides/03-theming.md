---
title: Theming
description: Change the look with CSS variables, a few overridden files, or a complete theme.
keywords: [theme, css, template, dark mode, customize, branding]
---

# Theming

The default theme is a light and dark site with a sidebar, an "On this page" navigation and a search dialog.
There are three levels of change, from small to large. Use the smallest one that does the job.

## 1. Settings

```yaml title="doctize.yml"
theme:
  accent: "#0d9488"
  extraCss: [brand.css]
  extraJs: [brand.js]
```

`accent` is the colour of links, the current page and highlights. `extraCss` and `extraJs` add your files after the
theme's own (the paths are relative to the configuration file).

## 2. CSS variables

Every colour, width and font of the default theme is a CSS custom property, so most restyling is a few lines
in an `extraCss` file:

```css title="brand.css"
:root {
  --accent: #0d9488;
  --content-width: 900px;
  --font-sans: "Inter", system-ui, sans-serif;
}
```

| Variable | Controls |
|----------|----------|
| `--accent`, `--accent-contrast` | links, the current page, buttons; the text colour on top of the accent |
| `--bg`, `--bg-subtle`, `--bg-raised` | page background, tables and code backgrounds, dialogs and cards |
| `--text`, `--text-muted`, `--border` | text, secondary text, lines |
| `--code-bg`, `--shadow` | code blocks, the search dialog |
| `--note`, `--tip`, `--important`, `--warning`, `--caution` | the colours of the alert types |
| `--tok-comment`, `--tok-string`, `--tok-keyword`, `--tok-number`, `--tok-type`, `--tok-function`, `--tok-annotation`, `--tok-property` | syntax highlighting |
| `--font-sans`, `--font-mono` | fonts |
| `--topbar-height`, `--sidebar-width`, `--toc-width`, `--content-width`, `--radius` | layout |

### Dark mode

The dark palette is defined on `:root[data-theme="dark"]` (chosen with the toggle) and, for readers who never
used the toggle, under `prefers-color-scheme: dark`. To change a colour in dark mode as well, override it in
both places:

```css title="brand.css"
:root { --accent: #0d9488; }

:root[data-theme="dark"] { --accent: #5eead4; }

@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) { --accent: #5eead4; }
}
```

## 3. A theme directory

A theme is a directory that is **layered over the built-in theme**. It contains only the files you change; every
other file comes from the built-in theme, so you keep receiving improvements to everything you did not touch.

```yaml title="doctize.yml"
theme:
  path: my-theme
```

```text
my-theme/
  templates/
    layout.mustache                 the frame around every page
    home.mustache                   the home page
    guide.mustache                  a guide or prose page
    api-type.mustache               an API page
    partials/
      head.mustache                 <head>: title, stylesheets, the colour scheme script
      header.mustache               the top bar
      sidebar.mustache              the navigation
      toc.mustache                  "On this page"
      pager.mustache                previous and next
      footer.mustache               the footer
      search.mustache               the search dialog
      member.mustache               one member of an API page
      parameters.mustache           a parameter table
      tags.mustache                 labelled tag blocks
  assets/
    css/theme.css                   replaces the built-in stylesheet
    img/logo.svg                    a new file, published as assets/img/logo.svg
```

Files in `assets/` are copied to `assets/` in the site. A file with the same path as a built-in asset replaces it.

### Example: your own footer

Create `my-theme/templates/partials/footer.mustache`:

```html title="footer.mustache"
<footer class="footer">
  <p>{{project.name}} {{project.version}} · <a href="https://example.com/imprint">Imprint</a></p>
</footer>
```

That is all. Every page uses the new footer and everything else stays as it was.

### Example: an announcement banner

Pass a text through the configuration, print it in a template:

```yaml title="doctize.yml"
theme:
  path: my-theme
  options:
    banner: "Version 2 is out"
```

```html title="my-theme/templates/partials/header.mustache (added at the top)"
{{#theme.options.banner}}<div class="banner">{{theme.options.banner}}</div>{{/theme.options.banner}}
```

Because the header partial is replaced as a whole, copy the built-in `header.mustache` from the Doctize
sources (`doctize-core/src/main/resources/dev/doctize/theme/default/templates/partials/`) and change it.

## Writing templates

Templates are [Mustache](https://mustache.github.io/) templates. Mustache is *logic-less*: a template can print
values, loop over lists and test whether something is present, but not compute anything, so all the work
happens in Java, and the data a template can use is listed in the [template reference](04-template-reference.md).

| Syntax | Meaning |
|--------|---------|
| `{{name}}` | print a value, HTML-escaped |
| `{{{contentHtml}}}` | print HTML as it is; every field ending in `Html` is meant for this |
| `{{#items}}...{{/items}}` | repeat for every item of a list; for a value, render if it is present and not empty |
| `{{^items}}...{{/items}}` | render if the list is empty or the value is absent |
| `{{-first}}`, `{{-last}}`, `{{-index}}` | inside a loop: the first or last item, the position |
| `{{> partials/footer}}` | include another template |
| `{{missing}}` | prints nothing, and does not fail |

**URLs.** All URLs in the data are relative to the site root. Print them as `{{basePath}}{{url}}` and the site keeps
working under any sub-path and from `file://`. `basePath` is `""` on the home page, `"../"` one folder deeper.

**Rendering.** Every page is rendered twice: first its body (`guide`, `api-type` or `home`) into `contentHtml`, then
`layout` around it. To change the frame of all pages, edit `layout` or its partials; to change what an API page
shows, edit `api-type` or `member`.

## Adding behaviour

The default scripts are small and independent: `search-engine.js` (the search), `search-ui.js` (the dialog),
`app.js` (colour scheme, tabs, copy buttons, navigation drawer). Your `extraJs` runs after them, and
`window.DoctizeSearch` is available if you want to build another search interface.

> [!NOTE]
> Scripts are classic scripts, not modules, because browsers refuse to load modules from `file://`. Doctize
> sites work when opened from disk, and your additions should as well.
