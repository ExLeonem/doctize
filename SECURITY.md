# Security policy

## Supported versions

Security fixes are made for the latest released version.

## Reporting a vulnerability

Please **do not open a public issue** for a security problem. Report it privately through
[GitHub's private vulnerability reporting](https://github.com/ExLeonem/doctize/security/advisories/new).
Describe what you found and how to reproduce it. You will get an answer within a few days; a confirmed problem is fixed
and disclosed together with a release.

## What is in scope

Doctize generates static files from source code and Markdown you provide. Relevant problems include:

- generated pages that execute content from documentation sources in unintended ways
  (Markdown is rendered with raw HTML allowed, on purpose, because documentation is authored by the project's own
  team; do not run Doctize on documentation you do not trust and publish the result on a domain that has credentials),
- the generator writing files outside of its output directory,
- the `--serve` preview server exposing more than the generated site.
