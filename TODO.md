# TODO: first release on Maven Central

The build is ready: `./gradlew build centralBundle` passes, and the bundle holds all six modules plus the Gradle plugin
marker. What is left is below; the one-time setup steps are described in `RELEASING.md`.

## Must fix before the first release

- [ ] **Make the GitHub repository public.** `https://github.com/ExLeonem/doctize` returns 404 to anyone not logged in.
      Several things point there: the POM's `url`, `scm` and `issueManagement`, the docs site (`exleonem.github.io/doctize`,
      which GitHub Pages won't serve from a private repository on a free plan), and the source links in the generated docs.
- [ ] **Fix the workflow triggers.** `.github/workflows/ci.yml` and `docs.yml` trigger on `branches: [main]`, but the
      branches are `master` and `dev`. CI only runs on pull requests and the docs site is never deployed. Change the
      trigger to `master` (and maybe `dev` for CI).
- [ ] **Fix the changelog.** `0.1.0` was never released (no tag, nothing under `dev.doctize` on Central), yet
      `CHANGELOG.md` has a `[0.1.0]` section and a large `[Unreleased]` section above it. Either merge the two into
      `0.1.0` (recommended) or release as `0.2.0`.
- [ ] **Merge `dev` into `master`** (`dev` is 16 commits ahead) and tag the release on `master`.
- [ ] **Check the one-time setup** (see `RELEASING.md`):
  - [ ] The namespace `dev.doctize` shows as **Verified** in the Central Portal (`doctize.dev` has a TXT record that
        looks like the verification key).
  - [ ] The GPG public key is on `keys.openpgp.org`, with the e-mail address confirmed.
  - [ ] The secrets `SIGNING_KEY`, `SIGNING_PASSWORD`, `CENTRAL_PORTAL_USERNAME` and `CENTRAL_PORTAL_PASSWORD` exist.
  - [ ] The `release` environment exists (optionally with required reviewers).
  - [ ] A Gradle Plugin Portal API key is stored as `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET`; the first
        publication of `dev.doctize` waits for a review by the Gradle team.

## Decide before 0.1.0 (released versions can't be changed)

- [ ] **Java 25 for everything.** `doctize.java-conventions` compiles every module, including `doctize-annotations`,
      with `--release 25`, so anyone who puts `@Doc` on their code has to compile with JDK 25. The annotations could
      target 11 or 17 cheaply; the processor is harder because it depends on `doctize-core`.
- [x] **Gradle Plugin Portal.** The plugin is only on Central, so users need
      `pluginManagement { repositories { mavenCentral() } }`. Also publishing via `com.gradle.plugin-publish` would make
      `id("dev.doctize")` work without that.
- [ ] **Javadoc jar size.** Each javadoc jar is about 4 MB, even for `doctize-themes`, because JDK 25's javadoc bundles
      its web fonts. The `--no-fonts` option should drop them and shrink the bundle from 29 MB to roughly 5 MB.

## Small cleanups

- [ ] `RELEASING.md` says "the five modules" and leaves out `doctize-themes`.

## First release

- [ ] Publish with the default `USER_MANAGED` mode. The Portal validates the upload (signatures, POMs, checksums)
      without publishing it, and the deployment can be dropped and retried. Press **Publish** only once it shows as
      validated.
