package dev.doctize.config;

import dev.doctize.annotations.Doc;

/**
 * Descriptive data about the documented project, shown in the header, the home page and the footer.
 *
 * @param name the project name
 * @param tagline a one-line description shown on the home page
 * @param version the released version, may be empty
 * @param repository URL of the source repository, may be empty
 * @param footer text shown in the page footer, may be empty
 */
@Doc(group = "Configuration", order = 21)
public record ProjectInfo(String name, String tagline, String version, String repository, String footer) {

    public ProjectInfo {
        name = orDefault(name, "Documentation");
        tagline = orDefault(tagline, "");
        version = orDefault(version, "");
        repository = orDefault(repository, "");
        footer = orDefault(footer, "");
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
