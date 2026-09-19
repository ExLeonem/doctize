package dev.doctize.model;

import dev.doctize.annotations.Doc;
import java.util.List;

/**
 * An annotated element together with its raw documentation comment, as extracted at compile time.
 *
 * @param role whether the element is an API type or a guide page
 * @param declaration what the element is
 * @param comment the raw Javadoc text, without comment delimiters; empty if there is none
 * @param placement where the element asked to be placed
 * @param keywords additional search terms
 * @param members documented members, only for {@link Role#API} types
 */
@Doc(group = "Source model", order = 41)
public record SourceElement(
        Role role,
        Declaration declaration,
        String comment,
        Placement placement,
        List<String> keywords,
        List<SourceElement> members) {

    public SourceElement {
        comment = comment == null ? "" : comment;
        placement = placement == null ? Placement.none() : placement;
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        members = members == null ? List.of() : List.copyOf(members);
    }
}
