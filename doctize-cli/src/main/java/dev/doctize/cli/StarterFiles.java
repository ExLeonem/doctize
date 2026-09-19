package dev.doctize.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/** The files {@code doctize init} creates, keyed by their path relative to the project directory. */
final class StarterFiles {

    static final String WORKFLOW_PATH = ".github/workflows/docs.yml";

    private StarterFiles() {}

    static Map<String, String> of(String projectName, boolean githubPages) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("doctize.yml", config(projectName, githubPages));
        files.put("docs/index.md", """
                Welcome! Pick a section below, or press <kbd>/</kbd> to search.
                """);
        files.put("docs/quick-start/01-first-steps.md", """
                ---
                title: First steps
                order: 1
                ---

                # First steps

                Describe the smallest useful thing a new user can do.

                ```java
                var greeting = new Greeter().greet("world");
                ```
                """);
        files.put("docs/setup/installation.md", """
                # Installation

                ```groovy tab="Gradle"
                implementation 'com.example:my-library:1.0.0'
                ```

                ```xml tab="Maven"
                <dependency>
                  <groupId>com.example</groupId>
                  <artifactId>my-library</artifactId>
                  <version>1.0.0</version>
                </dependency>
                ```
                """);
        if (githubPages) {
            files.put(WORKFLOW_PATH, workflow());
        }
        return files;
    }

    private static String config(String projectName, boolean githubPages) {
        String repository = githubPages
                ? "  # Set this to your repository: it is used to work out where GitHub Pages serves the site.\n  # repository: https://github.com/OWNER/REPOSITORY\n"
                : "";
        String hosting = githubPages
                ? """

                # Prepares the output for GitHub Pages. The address of the site is derived from project.repository;
                # set baseUrl if you use a custom domain or another host.
                hosting:
                  githubPages: true
                  # baseUrl: https://docs.example.com/
                """
                : "";
        return """
                project:
                  name: %s
                  tagline: One sentence that says what this project is for.
                %sguides: docs
                %s
                # The order of this list is the order of the navigation.
                # Add a section here, then create docs/<section-id>/ and put Markdown files in it.
                sections:
                  - id: quick-start
                    title: Quick start
                    description: Up and running in five minutes.
                  - id: setup
                    title: Setup
                  - id: api
                    title: API Reference
                """.formatted(projectName, repository, hosting);
    }

    /** A workflow that builds the site and deploys it with the official GitHub Pages actions. */
    private static String workflow() {
        return """
                # Publishes the documentation to GitHub Pages on every push to the default branch.
                # One-time setup: Settings → Pages → Build and deployment → Source: "GitHub Actions".
                name: Documentation

                on:
                  push:
                    branches: [main]
                  workflow_dispatch:

                permissions:
                  contents: read
                  pages: write
                  id-token: write

                concurrency:
                  group: pages
                  cancel-in-progress: true

                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/setup-java@v4
                        with:
                          distribution: temurin
                          java-version: '25'
                      - id: pages
                        uses: actions/configure-pages@v5
                      # `generateDocs` is a Gradle task that runs `doctize generate`, see "Running from Gradle"
                      # in the Doctize documentation. Adjust the command if you build the site differently.
                      - name: Generate the documentation
                        run: ./gradlew generateDocs -PdoctizeArgs="--github-pages --base-url ${{ steps.pages.outputs.base_url }}"
                      - uses: actions/upload-pages-artifact@v3
                        with:
                          path: build/docs

                  deploy:
                    needs: build
                    runs-on: ubuntu-latest
                    environment:
                      name: github-pages
                      url: ${{ steps.deployment.outputs.page_url }}
                    steps:
                      - id: deployment
                        uses: actions/deploy-pages@v4
                """;
    }
}
