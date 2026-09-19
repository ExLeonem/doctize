package dev.doctize.javadoc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts an inline {@code {@snippet}} tag with inline content into a fenced code block. */
final class Snippet {

    private static final Pattern LANG = Pattern.compile("lang\\s*=\\s*\"?([\\w+-]+)\"?");

    private Snippet() {}

    static String toMarkdown(String body) {
        int colon = body.indexOf(':');
        if (colon < 0) {
            return Markdown.code(body);
        }
        Matcher lang = LANG.matcher(body.substring(0, colon));
        String language = lang.find() ? lang.group(1) : "java";
        String code = body.substring(colon + 1).stripIndent().strip();
        return "\n```" + language + "\n" + code + "\n```\n";
    }
}
