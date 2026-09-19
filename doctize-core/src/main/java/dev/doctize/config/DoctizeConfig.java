package dev.doctize.config;

import dev.doctize.annotations.Doc;
import java.util.List;

/**
 * The complete configuration of a documentation site, usually read from {@code doctize.yml}.
 *
 * @param project descriptive project data
 * @param theme appearance settings
 * @param sections the declared sections, in navigation order; other sections are appended on demand
 * @param guides directory with Markdown guides, relative to the config file, may be empty
 * @param defaultSection section for guides that are not in a sub-directory and do not name a section
 * @param api API reference settings
 */
@Doc(group = "Configuration", order = 20)
public record DoctizeConfig(
        ProjectInfo project,
        ThemeSettings theme,
        List<SectionSpec> sections,
        String guides,
        String defaultSection,
        ApiSettings api) {

    public static final String DEFAULT_GUIDE_SECTION = "Guides";

    public DoctizeConfig {
        project = project == null ? new ProjectInfo(null, null, null, null, null) : project;
        theme = theme == null ? ThemeSettings.defaults() : theme;
        sections = sections == null ? List.of() : List.copyOf(sections);
        guides = guides == null ? "" : guides;
        defaultSection = defaultSection == null || defaultSection.isBlank() ? DEFAULT_GUIDE_SECTION : defaultSection;
        api = api == null ? ApiSettings.defaults() : api;
    }

    public static DoctizeConfig defaults() {
        return new DoctizeConfig(null, null, null, null, null, null);
    }
}
