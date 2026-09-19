package dev.doctize.javadoc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A parsed Javadoc comment: the main description followed by block tags.
 *
 * @param description the text before the first block tag, still containing inline tags
 * @param tags the block tags in source order
 */
public record JavadocComment(String description, List<BlockTag> tags) {

    public static final JavadocComment EMPTY = new JavadocComment("", List.of());

    public JavadocComment {
        tags = List.copyOf(tags);
    }

    public boolean isEmpty() {
        return description.isBlank() && tags.isEmpty();
    }

    public List<BlockTag> tags(String name) {
        return tags.stream().filter(tag -> tag.name().equals(name)).toList();
    }

    public Optional<BlockTag> firstTag(String name) {
        return tags.stream().filter(tag -> tag.name().equals(name)).findFirst();
    }

    public Optional<BlockTag> paramTag(String parameterName) {
        return tags("param").stream().filter(tag -> tag.subject().equals(parameterName)).findFirst();
    }

    /** Finds the {@code @throws} (or {@code @exception}) tag for an exception, matching simple or qualified names. */
    public Optional<BlockTag> throwsTag(String exceptionType) {
        String simple = simpleName(exceptionType);
        return tags.stream()
                .filter(tag -> tag.name().equals("throws") || tag.name().equals("exception"))
                .filter(tag -> simpleName(tag.subject()).equals(simple))
                .findFirst();
    }

    /** Tags whose names are not in {@code handled}; these are rendered generically. */
    public List<BlockTag> tagsExcept(Set<String> handled) {
        return tags.stream().filter(tag -> !handled.contains(tag.name())).toList();
    }

    private static String simpleName(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }
}
