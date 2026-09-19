package dev.doctize.markdown;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown split into a leading level-1 heading and the rest. Pages take their title from that
 * heading, so it is removed from the body to avoid showing the title twice.
 */
public record TitledMarkdown(Optional<String> title, String body) {

    private static final Pattern LEADING_H1 = Pattern.compile("\\A\\s*#[ \\t]+(.+?)[ \\t]*#*[ \\t]*(?:\\R|\\z)");

    public static TitledMarkdown split(String markdown) {
        Matcher matcher = LEADING_H1.matcher(markdown);
        if (!matcher.find()) {
            return new TitledMarkdown(Optional.empty(), markdown);
        }
        return new TitledMarkdown(Optional.of(matcher.group(1).strip()), markdown.substring(matcher.end()).strip());
    }
}
