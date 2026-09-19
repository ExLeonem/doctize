package dev.doctize.render;

import java.util.Map;
import java.util.regex.Pattern;

/** Display names for Javadoc block tags; unknown tags get a label derived from their name. */
final class TagLabels {

    private static final Map<String, String> KNOWN = Map.ofEntries(
            Map.entry("since", "Since"),
            Map.entry("author", "Author"),
            Map.entry("version", "Version"),
            Map.entry("see", "See also"),
            Map.entry("apiNote", "API note"),
            Map.entry("implNote", "Implementation note"),
            Map.entry("implSpec", "Implementation requirements"),
            Map.entry("example", "Example"),
            Map.entry("usage", "Usage"),
            Map.entry("note", "Note"),
            Map.entry("todo", "To do"));

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z])(?=[A-Z])");

    private TagLabels() {}

    static String labelFor(String tagName) {
        String known = KNOWN.get(tagName);
        if (known != null) {
            return known;
        }
        String words = CAMEL_BOUNDARY.matcher(tagName).replaceAll(" ").replace('-', ' ').toLowerCase();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
