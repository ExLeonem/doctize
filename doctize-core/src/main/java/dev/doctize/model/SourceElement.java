package dev.doctize.model;

import dev.doctize.annotations.Doc;
import java.util.List;
import java.util.Map;

/**
 * An annotated element together with its raw documentation comment, as extracted at compile time.
 *
 * @param role whether the element is an API type or a guide page
 * @param declaration what the element is
 * @param comment the raw Javadoc text, without comment delimiters; empty if there is none
 * @param placement where the element asked to be placed
 * @param keywords additional search terms
 * @param members documented members, only for {@link Role#API} types
 * @param snippets the external files that the {@code {@snippet}} tags of {@code comment} show, read at compile time;
 *     keyed by {@code file:<path>} or {@code class:<name>}, as the tag names them
 * @param location where the element is declared; {@code null} if the source is not in a Git repository
 */
@Doc(group = "Source model", order = 41)
public record SourceElement(
        Role role,
        Declaration declaration,
        String comment,
        Placement placement,
        List<String> keywords,
        List<SourceElement> members,
        Map<String, String> snippets,
        SourceLocation location) {

    public SourceElement(
            Role role, Declaration declaration, String comment, Placement placement, List<String> keywords, List<SourceElement> members) {
        this(role, declaration, comment, placement, keywords, members, Map.of(), null);
    }

    public SourceElement {
        comment = comment == null ? "" : comment;
        placement = placement == null ? Placement.none() : placement;
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        members = members == null ? List.of() : List.copyOf(members);
        snippets = snippets == null ? Map.of() : Map.copyOf(snippets);
    }
}
