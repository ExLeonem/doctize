package dev.doctize.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.doctize.annotations.Doc;
import dev.doctize.text.Slugs;

/**
 * A section of the documentation, e.g. "Quick start". The order of the sections in the
 * configuration is the order in the navigation.
 *
 * <p>In YAML a section can be written as a plain title ({@code - Quick start}) or as a mapping
 * with {@code id}, {@code title} and {@code description}.
 *
 * @param id used in URLs, and to refer to the section from pages; derived from the title if blank
 * @param title the display title; derived from the id if blank
 * @param description a short teaser shown on the home page, may be empty
 */
@Doc(group = "Configuration", order = 23)
public record SectionSpec(String id, String title, String description) {

    public SectionSpec {
        description = description == null ? "" : description;
        boolean hasTitle = title != null && !title.isBlank();
        boolean hasId = id != null && !id.isBlank();
        if (!hasTitle && !hasId) {
            throw new IllegalArgumentException("A section needs an id or a title");
        }
        title = hasTitle ? title : Slugs.humanize(id);
        id = hasId ? Slugs.slugify(id) : Slugs.slugify(title);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SectionSpec ofTitle(String title) {
        return new SectionSpec("", title, "");
    }

    /** Whether a page's {@code section} value, given as id or title, refers to this section. */
    public boolean matches(String reference) {
        String slug = Slugs.slugify(reference);
        return slug.equals(id) || slug.equals(Slugs.slugify(title));
    }
}
