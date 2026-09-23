package dev.doctize.config;

import dev.doctize.annotations.Doc;

/**
 * Descriptive data about the documented project, shown in the header, the home page and the footer.
 *
 * @param name the project name, {@value #DEFAULT_NAME} if none is given
 * @param tagline a one-line description shown on the home page
 * @param version the released version, may be empty
 * @param repository URL of the source repository, may be empty
 * @param footer text shown in the page footer, may be empty
 * @param sourceUrl the address of a line in a source file, with the placeholders {@code {path}} (relative to the root of
 *     the repository) and {@code {line}}, e.g. {@code https://git.acme.com/cache/-/blob/main/{path}#L{line}}; may be
 *     empty, then it is derived from {@code repository} on GitHub, GitLab, Bitbucket and Codeberg
 */
@Doc(group = "Configuration", order = 21)
public record ProjectInfo(String name, String tagline, String version, String repository, String footer, String sourceUrl) {

    /** The name of a project that has none. */
    public static final String DEFAULT_NAME = "Documentation";

    public ProjectInfo(String name, String tagline, String version, String repository, String footer) {
        this(name, tagline, version, repository, footer, null);
    }

    public ProjectInfo {
        name = orDefault(name, DEFAULT_NAME);
        tagline = orDefault(tagline, "");
        version = orDefault(version, "");
        repository = orDefault(repository, "");
        footer = orDefault(footer, "");
        sourceUrl = orDefault(sourceUrl, "");
    }

    /**
     * This project data, with every value that is not set taken from {@code fallback}; used for what the build tool knows
     * about a project, which the configuration file may override.
     */
    public ProjectInfo orElse(ProjectInfo fallback) {
        return new ProjectInfo(
                name.isBlank() || name.equals(DEFAULT_NAME) ? fallback.name : name,
                tagline.isBlank() ? fallback.tagline : tagline,
                version.isBlank() ? fallback.version : version,
                repository.isBlank() ? fallback.repository : repository,
                footer.isBlank() ? fallback.footer : footer,
                sourceUrl.isBlank() ? fallback.sourceUrl : sourceUrl);
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
