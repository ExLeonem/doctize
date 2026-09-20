# Releasing

This document is for maintainers. A release publishes the five modules (`doctize-annotations`, `doctize-processor`,
`doctize-core`, `doctize-cli`, `doctize-gradle-plugin`) to Maven Central and creates a GitHub release with the command line distribution.

## One-time setup

### 1. A Maven Central account and a namespace

1. Sign in at <https://central.sonatype.com/> and open **Namespaces**.
2. Register the namespace **`dev.doctize`**. Central asks you to prove that you own it: for a namespace `dev.doctize`
   you must control the domain **`doctize.dev`** and add the verification key as a DNS `TXT` record.
3. If you do not own that domain, choose a namespace you can verify instead, for example
   `io.github.exleonem` (verified by creating a public repository with a given name in your GitHub account), and change
   `group` in `buildSrc/src/main/kotlin/doctize.java-conventions.gradle.kts`, the coordinates in the documentation
   (`grep -r "dev.doctize:" .`), and `PublishedArtifactsSystemTest`. The Java package names can stay as they are.
   The Gradle plugin needs one more change: its *marker artifact*, which lets `plugins { id("dev.doctize") }` find it, is
   published under the plugin id as its group. Change the id in the `gradlePlugin` block of
   `doctize-gradle-plugin/build.gradle.kts` to one below your namespace, and update `GradlePluginSystemTest`, the marker
   test in `PublishedArtifactsSystemTest` and the `id("dev.doctize")` snippets in the documentation.
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

## Licenses

Licenses unlock the advanced features (see the guide *Licensing*). Doctize only contains the **public** key
(`doctize-core/src/main/resources/dev/doctize/license/vendor-public-key.pem`), so it can check licenses but not issue them. Whoever
holds the **private** key issues licenses, with the tool in `doctize-licensing`, which is not published.

### The signing key

The key pair that is in the repository was created with `keygen`; its private half is in `.license/vendor-private-key.pem`, which
is ignored by Git. **Move it to a place only you control** (a password manager, a secret store), and treat losing it like losing the
release signing key: nobody can issue licenses without it. Anyone who has it can issue licenses.

To create a new pair, for instance because the key leaked (every license issued with the old key stops working, so reissue them):

```console
$ ./gradlew :doctize-licensing:run --args="keygen --force --out $PWD/.license/vendor-private-key.pem --public-out $PWD/doctize-core/src/main/resources/dev/doctize/license/vendor-public-key.pem"
```

then release a new version, since the public key is part of `doctize-core`. Users must update Doctize to use the new licenses.

### Issuing a license

```console
$ ./gradlew :doctize-licensing:run --args="issue --key $PWD/.license/vendor-private-key.pem --licensee 'Acme Corp' --expires 2027-12-31 --feature architecture --out $PWD/acme.license"
```

`--expires` is the last valid day; leave it out for a license that does not expire; without `--feature`, all features are unlocked.
The result is one line of text, with comments. Send it to the customer, and keep a record of the `--id` that was printed.
A license cannot be revoked, only allowed to expire, because Doctize never asks a server. Prefer short validity for that reason.

### The live architecture map in the documentation

The documentation of Doctize includes a live map of its own architecture when the build has a license. To have it on the published
site, issue a license for yourself (see above; give it an expiry, and renew it before then) and store its text as the repository secret
`DOCTIZE_LICENSE`. The workflow `.github/workflows/docs.yml` passes it on. Without the secret, or with an expired one that is
not empty, the site is built without the page; an empty secret is ignored, an unusable one makes the build fail with the reason.
Locally, put the file at `doctize-docs/doctize.license` (ignored by Git) or in `~/.doctize/license`.

### Tests that need the key

The tests of the licensed path with the real, installed product (`ArchitectureSystemTest` and parts of `GradlePluginSystemTest`) need a
license that the real public key accepts. They read the private key from `.license/vendor-private-key.pem`, or from
`-Pdoctize.license.privateKey=<file>`, and are **skipped** where there is none, such as a build on GitHub Actions. To run them there,
provide the key as a secret and write it to a file in the workflow. Everything else about licensing is tested with keys made for the tests.

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
