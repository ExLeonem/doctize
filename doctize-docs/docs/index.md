Doctize turns the **Javadoc of your annotated Java code** and a folder of **Markdown guides** into a
modern, searchable documentation site. The structure of the site is yours: "Quick start", "Setup",
"Recipes" or anything else is a section, and adding one takes a folder or one line of YAML.

Press <kbd>/</kbd> anywhere on this site to search it.

> [!NOTE]
> You are reading a site that Doctize generated **from itself**: the API Reference comes from the Javadoc in
> Doctize's own source code, everything else from the Markdown files in `doctize-docs/docs`.
> Run `./gradlew :doctize-docs:generateDocs` to build it.

## Why Doctize

- **Docs where they belong.** Put `@Doc` on a type and its Javadoc becomes a reference page. Write
  classic `/** */` comments or Markdown `///` comments.
- **A structure you control.** Sections and their order come from `doctize.yml`, folders, front matter or code.
- **Search that finds things.** Typo tolerant, camelCase aware, offline, with links straight to API members.
- **A theme you can change.** Light and dark mode; override one template, one stylesheet, or a few CSS variables.
- **Plain static output.** Host it anywhere, or open it from your disk.
