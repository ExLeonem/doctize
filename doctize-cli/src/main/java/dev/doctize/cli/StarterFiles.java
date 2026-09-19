package dev.doctize.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/** The files {@code doctize init} creates, keyed by their path relative to the project directory. */
final class StarterFiles {

    private StarterFiles() {}

    static Map<String, String> of(String projectName) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("doctize.yml", """
                project:
                  name: %s
                  tagline: One sentence that says what this project is for.

                guides: docs

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
                """.formatted(projectName));
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
        return files;
    }
}
