package dev.doctize.site;

import dev.doctize.config.ProjectInfo;
import java.util.List;

/**
 * The structure of the whole documentation: non-empty sections in navigation order.
 *
 * @param project descriptive project data
 * @param sections the sections
 * @param intro Markdown introduction for the home page, may be empty
 */
public record Site(ProjectInfo project, List<Section> sections, String intro) {

    public Site {
        sections = List.copyOf(sections);
        intro = intro == null ? "" : intro;
    }

    /** All pages in reading order: section by section. */
    public List<SitePage> pages() {
        return sections.stream().flatMap(section -> section.pages().stream()).toList();
    }
}
