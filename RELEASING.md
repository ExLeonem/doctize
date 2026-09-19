# Releasing

This document is for maintainers. A release publishes the four modules (`doctize-annotations`, `doctize-processor`,
`doctize-core`, `doctize-cli`) to Maven Central and creates a GitHub release with the command line distribution.

## One-time setup

### 1. A Maven Central account and a namespace

1. Sign in at <https://central.sonatype.com/> and open **Namespaces**.
2. Register the namespace **`dev.doctize`**. Central asks you to prove that you own it: for a namespace `dev.doctize`
   you must control the domain **`doctize.dev`** and add the verification key as a DNS `TXT` record.
3. If you do not own that domain, choose a namespace you can verify instead, for example
   `io.github.exleonem` (verified by creating a public repository with a given name in your GitHub account), and change
   `group` in `buildSrc/src/main/kotlin/doctize.java-conventions.gradle.kts`, the coordinates in the documentation
   (`grep -r "dev.doctize:" .`), and `PublishedArtifactsSystemTest`. The Java package names can stay as they are.
4. Under **Account → Generate User Token**, create a token. You get a username and a password: these are
   `CENTRAL_PORTAL_USERNAME` and `CENTRAL_PORTAL_PASSWORD`.

### 2. A signing key

```console
$ gpg --full-generate-key                       # RSA 4096, no expiry (or one you will renew), with a passphrase
$ gpg --list-secret-keys --keyid-format long    # note the key id
$ gpg --keyserver keys.openpgp.org --send-keys <key id>
$ gpg --armor --export-secret-keys <key id>     # the value of SIGNING_KEY
```

Central checks signatures against public keyservers, so the public key must be published (`keys.openpgp.org` requires you
to confirm your e-mail address once).

### 3. Repository secrets

In **Settings → Secrets and variables → Actions** add:

| Secret | Value |
|--------|-------|
| `SIGNING_KEY` | the armored private key from above, including the `BEGIN`/`END` lines |
| `SIGNING_PASSWORD` | its passphrase |
| `CENTRAL_PORTAL_USERNAME` | the token username |
| `CENTRAL_PORTAL_PASSWORD` | the token password |

Optionally create an environment named `release` with required reviewers: the release job then waits for approval.
Set the repository variable `CENTRAL_PUBLISHING_TYPE` to `AUTOMATIC` to publish without pressing a button in the Portal
(the default, `USER_MANAGED`, keeps the upload in the Portal until you publish it, which is the safer choice for the first releases).

## Releasing a version

1. Choose the version, e.g. `0.2.0`. In one commit:
   - set `version=0.2.0` in `gradle.properties`,
   - move the "Unreleased" entries of `CHANGELOG.md` to a new `## [0.2.0]` section and update the links at the bottom,
   - update the version in `doctize-docs/doctize.yml` and in the installation snippets of `doctize-docs/docs`
     (`./gradlew :doctize-docs:verifyDocsVersion` tells you what is missing).
2. Run `./gradlew build` locally. It runs all tests, generates the documentation, and checks the version.
3. Merge, then tag and push:

   ```console
   $ git tag v0.2.0
   $ git push origin v0.2.0
   ```

4. The **Release** workflow verifies that the tag matches `gradle.properties` and that the changelog has a section, runs
   the build with the signing key (which also runs the checks of the signatures), uploads the bundle to Central and creates
   the GitHub release.
5. With `USER_MANAGED` publishing, open <https://central.sonatype.com/publishing/deployments>, check the deployment, and press
   **Publish**. The artifacts appear on Maven Central within about half an hour.
6. Set `version` in `gradle.properties` to the next planned version so that development builds do not claim the released one.

Versions on Maven Central can never be changed or deleted. If something is wrong, release a new version.

## Trying it without releasing

Everything up to the upload can be run locally.

```console
$ ./gradlew centralBundle          # → build/central/doctize-<version>-central-bundle.zip, unsigned
$ ./gradlew :doctize-tests:systemTest   # inspects the staged artifacts: POM metadata, jars, checksums, a consumer build
$ ./gradlew publishToMavenLocal    # install into ~/.m2 to try the artifacts in another project
```

To exercise signing without your real key, create a throwaway key in an empty keyring and pass it as a property:

```console
$ export GNUPGHOME=$(mktemp -d)
$ gpg --batch --pinentry-mode loopback --passphrase '' --quick-generate-key test@example.invalid rsa3072 sign 1d
$ ./gradlew centralBundle -PsigningInMemoryKey="$(gpg --armor --export-secret-keys test@example.invalid)" -PsigningInMemoryKeyPassword=""
```

## What the build checks for you

- `PublishedArtifactsSystemTest` checks what Central requires of every module: the jar, sources and javadoc jars, the POM
  (name, description, URL, license, developer, SCM), checksums, and, when a key is configured, a signature for each file.
  It also checks that the core jar contains the whole default theme and that the jars are reproducible.
- `ConsumerSystemTest` resolves the artifacts from the staging repository like a downstream project would, compiles a library
  with the published processor, and generates its documentation with the published CLI.
- `:doctize-docs:verifyDocsVersion` keeps the documentation in step with the version.
- The release workflow refuses a tag that does not match `gradle.properties` or has no changelog section.
