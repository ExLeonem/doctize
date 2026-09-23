package dev.doctize.javadoc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@code {@snippet}} tag (JEP 413): its attributes, and its inline content if it has any. The content follows a
 * colon, on the next line; without it, the snippet comes from the file named by {@code file} or {@code class}.
 *
 * @param attributes the attributes, e.g. {@code lang}, {@code file}, {@code class}, {@code region}; unquoted
 * @param content the inline content, empty for an external snippet
 */
public record SnippetTag(Map<String, String> attributes, Optional<String> content) {

    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s:]+))");
    private static final String OPENING = "{@snippet";

    public SnippetTag {
        attributes = Map.copyOf(attributes);
    }

    /** Parses the body of the tag: everything after the tag name, up to the closing brace. */
    public static SnippetTag parse(String body) {
        int colon = colonOutsideQuotes(body);
        String head = colon < 0 ? body : body.substring(0, colon);
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(head);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            attributes.put(matcher.group(1), value);
        }
        if (colon < 0) {
            return new SnippetTag(attributes, Optional.empty());
        }
        // The content starts on the line after the colon; text right after the colon is accepted, too.
        String rest = body.substring(colon + 1);
        int newline = rest.indexOf('\n');
        boolean restOfLineBlank = newline >= 0 && rest.substring(0, newline).isBlank();
        String content = restOfLineBlank ? rest.substring(newline + 1) : rest;
        return new SnippetTag(attributes, Optional.of(content));
    }

    /** All snippet tags in a documentation comment. */
    public static List<SnippetTag> findAll(String comment) {
        List<SnippetTag> tags = new ArrayList<>();
        int from = comment.indexOf(OPENING);
        while (from >= 0) {
            int start = from + OPENING.length();
            int end = closingBrace(comment, start);
            if (end < 0) {
                break;
            }
            if (start == end || Character.isWhitespace(comment.charAt(start))) {
                tags.add(parse(comment.substring(start, end)));
            }
            from = comment.indexOf(OPENING, end);
        }
        return tags;
    }

    public Optional<String> attribute(String name) {
        return Optional.ofNullable(attributes.get(name));
    }

    /**
     * The external file this snippet shows, as {@code file:<path>} or {@code class:<name>}; empty for an inline snippet.
     * The annotation processor stores the files under this key, see {@code SourceElement#snippets}.
     */
    public Optional<String> externalReference() {
        return attribute("file").map(file -> "file:" + file)
                .or(() -> attribute("class").map(name -> "class:" + name));
    }

    /** The language: the {@code lang} attribute, else the extension of the file, else Java. */
    public String language() {
        return attribute("lang").or(() -> attribute("file")
                        .filter(file -> file.lastIndexOf('.') > file.lastIndexOf('/'))
                        .map(file -> file.substring(file.lastIndexOf('.') + 1)))
                .orElse("java");
    }

    private static int colonOutsideQuotes(String body) {
        char quote = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quote != 0) {
                quote = c == quote ? 0 : quote;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == ':') {
                return i;
            }
        }
        return -1;
    }

    private static int closingBrace(String text, int from) {
        int depth = 1;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
