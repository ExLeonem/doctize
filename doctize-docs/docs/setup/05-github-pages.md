---
title: Publishing to GitHub Pages
description: Host the site on GitHub Pages, with a workflow that deploys it on every push.
keywords: [github, pages, hosting, deploy, actions, cname, custom domain, sitemap, 404]
---

# Publishing to GitHub Pages

A Doctize site is plain static files, so any web host can serve it. For [GitHub Pages](https://pages.github.com/)
Doctize does a little extra: it prepares the files GitHub expects, and it can scaffold the workflow that publishes
the site on every push.

> [!NOTE]
> The site works on GitHub Pages even without any of this. All its links are relative, so it does not mind being served
> from `https://<user>.github.io/<repository>/` instead of from the root of a host. The options below add what
> *needs* to know the address: the 404 page, the sitemap and canonical links.

## The short way

```console
$ doctize init --github-pages
created doctize.yml
created docs/index.md
created docs/quick-start/01-first-steps.md
created docs/setup/installation.md
created .github/workflows/docs.yml
```

1. Check that the Git remote `origin` is your GitHub repository (`git remote -v`); the address of the site is derived from it.
   If it is not, set `project.repository` in `doctize.yml`, e.g. to `https://github.com/acme/cache`.
2. In the repository, open **Settings → Pages → Build and deployment** and choose **GitHub Actions** as the source.
3. Push. The workflow generates the site and deploys it.

The starter workflow calls `./gradlew generateDocs`, a task that runs `doctize generate`; see
[Running from Gradle](03-gradle.md). Change the command if you generate the site differently.

## What the option does

Turn it on in the configuration file, on the command line, or in code:

```yaml tab="doctize.yml"
hosting:
  githubPages: true
```

```console tab="Command line"
$ doctize generate --github-pages
```

```java tab="Java"
Doctize.builder().githubPages() /* ... */;
```

| File | When | What it is for |
|------|------|----------------|
| `.nojekyll` | `githubPages` | Tells GitHub to publish the files exactly as generated. Without it GitHub runs the site through Jekyll, which can drop files and folders. |
| `CNAME` | `githubPages` and a custom domain | Makes GitHub serve the site at your own domain. |
| `404.html` | the address is known | The page GitHub shows for a missing URL. It uses absolute links, because it is shown at any depth. |
| `sitemap.xml`, `robots.txt` | the address is known | Help search engines find every page. |
| `<link rel="canonical">` and `og:` tags in every page | the address is known | Tell search engines and link previews the one true address of each page. |

## The address of the site

Doctize needs to know where the site will be published for everything in the second half of that table. The address
is the first of these that is set:

1. `hosting.baseUrl` (or `--base-url`): the full address, e.g. `https://acme.github.io/cache/`.
2. `hosting.customDomain` (or `--cname`): the address becomes `https://<domain>/`.
3. With `githubPages`: derived from `project.repository`, which by default is read from the Git remote `origin`. `https://github.com/Acme/cache` becomes
   `https://acme.github.io/cache/`, and a repository named `acme.github.io` is served at the root,
   `https://acme.github.io/`.

If `githubPages` is on but none of these gives an address, the site is still generated and Doctize prints a warning that
the 404 page, sitemap and canonical links were skipped.

> [!IMPORTANT]
> A project site is served **below a path** (`/cache/`). That path is part of the address; leave it in `baseUrl`. The 404 page
> needs it to find the stylesheet and the scripts.

## Custom domain

```yaml title="doctize.yml"
hosting:
  githubPages: true
  customDomain: docs.acme.dev
```

This writes `docs.acme.dev` to `CNAME` and makes `https://docs.acme.dev/` the address of the site. You still need a DNS
record at your domain provider (a `CNAME` record pointing to `<user>.github.io`); see GitHub's documentation on custom domains.

## The workflow

`doctize init --github-pages` writes this to `.github/workflows/docs.yml`. It is also what this very site uses:

```yaml title=".github/workflows/docs.yml (abridged)"
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '25' }
      - id: pages
        uses: actions/configure-pages@v5
      - run: ./gradlew generateDocs -PdoctizeArgs="--github-pages --base-url ${{ steps.pages.outputs.base_url }}"
      - uses: actions/upload-pages-artifact@v3
        with: { path: build/docs }

  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

- `configure-pages` tells the workflow the real address of the site, including a custom domain, so you never have to write it down.
  It is handed to Doctize as `--base-url`.
- `upload-pages-artifact` packages the output directory, and `deploy-pages` publishes it. The job needs the `pages: write` and
  `id-token: write` permissions, which the workflow declares.
- The actions are pinned to major versions. Update them from time to time; the workflow has not been run against a live repository yet, so check the first run.

The `-PdoctizeArgs` property is understood by the [Gradle plugin](03-gradle.md) and by the `JavaExec` task shown there
under *Without the plugin*; it appends arguments to the `doctize generate` call. The plugin also has the options
`--github-pages` and `--base-url` for the same purpose.

## Without GitHub Actions

Any branch can serve as the source. Generate the site, then push its contents to the branch, for example `gh-pages`:

```console
$ doctize generate --github-pages --base-url https://acme.github.io/cache/ --output build/docs
$ cd build/docs
$ git init -q && git add -A && git commit -q -m "Update documentation"
$ git push -f git@github.com:acme/cache.git HEAD:gh-pages
```

Then choose that branch under **Settings → Pages → Build and deployment → Deploy from a branch**.

## Other hosts

`hosting.baseUrl` alone, without `githubPages`, produces the 404 page, sitemap, `robots.txt` and canonical links, and none
of the GitHub-specific files. That is what you want on Netlify, Cloudflare Pages, S3 or your own web server; point them at the output
directory and, if they support it, at `404.html`.

## If something looks wrong

| Symptom | Likely cause |
|---------|--------------|
| The pages load but have no styling | You copied only the HTML files. The `assets/` folder must be published as well. |
| The whole site gives 404 | The Pages source is not set to "GitHub Actions" (or to your branch), or the first deployment has not finished. |
| The 404 page has no styling | The address has the wrong path: it must end with the repository name, e.g. `https://acme.github.io/cache/`. |
| Files starting with an underscore are missing | `.nojekyll` is not in the output; turn `githubPages` on. |
| A warning says the address is unknown | The Git remote `origin` is not on github.com (or there is no Git repository): set `project.repository` to a `github.com` URL, or set `hosting.baseUrl`. |
| The site cannot be reached from a private repository | Pages for private repositories needs a GitHub plan that includes it. |
