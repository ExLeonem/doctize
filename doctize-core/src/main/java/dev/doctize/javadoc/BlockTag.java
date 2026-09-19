package dev.doctize.javadoc;

/**
 * A block tag of a Javadoc comment, such as {@code @param name the name}.
 *
 * @param name the tag name without the {@code @}, e.g. {@code param}
 * @param subject the argument the tag refers to for {@code @param} and {@code @throws}, else empty
 * @param text the tag text; may span several lines and contain Markdown and inline tags
 */
public record BlockTag(String name, String subject, String text) {}
