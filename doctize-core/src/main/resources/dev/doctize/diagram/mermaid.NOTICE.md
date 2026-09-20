# Mermaid

`mermaid.min.js` is Mermaid, unmodified, from the npm package `mermaid@12.0.0` (`dist/mermaid.min.js`).

- Project: https://mermaid.js.org, source https://github.com/mermaid-js/mermaid
- License: MIT, see `mermaid.LICENSE` (Copyright (c) 2014 - 2022 Knut Sveidqvist)
- Tarball integrity, as published by the npm registry:
  `sha512-/wQXC9iBxoGV8p3erbvaXs9h77VyLDBH6GdayVjj3hEcSQhFU4N1WUhUppotCEqlIxI2pRMwjwBSwTB1MfZBgQ==`

The bundle contains other open source libraries (for example d3, DOMPurify, cytoscape, KaTeX and lodash) under their own licenses;
their notices that Mermaid's build keeps are inside the file.

Doctize copies it to `assets/vendor/mermaid.min.js` of a generated site, and only if a page has a Mermaid diagram.
To update it: `npm pack mermaid@<version>`, check the tarball against `npm view mermaid@<version> dist.integrity`, replace the file
and this notice, and run the tests.
