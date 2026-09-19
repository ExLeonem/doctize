package dev.doctize.site;

import dev.doctize.annotations.Doc;
import java.util.List;

/**
 * A page as delivered by a {@link PageSource}, before it is placed in the site structure.
 *
 * @param slug the file name without extension; made unique within its section by the assembler
 * @param title the page title
 * @param section the section, as id or title; blank for the default section
 * @param group navigation group label, may be blank
 * @param order sorting key within the section
 * @param description short description used in search results and cards, may be blank
 * @param keywords extra search terms
 * @param content what the page shows
 */
@Doc(group = "Extending", order = 31)
public record Page(
        String slug,
        String title,
        String section,
        String group,
        int order,
        String description,
        List<String> keywords,
        PageContent content) {

    public Page {
        section = section == null ? "" : section;
        group = group == null ? "" : group;
        description = description == null ? "" : description;
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
