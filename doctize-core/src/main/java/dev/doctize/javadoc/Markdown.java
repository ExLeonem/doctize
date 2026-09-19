package dev.doctize.javadoc;

/**
 * Helpers for emitting text that renders literally, whether the surrounding Javadoc is Markdown
 * or classic HTML. Inline tags produce HTML with character references instead of Markdown syntax,
 * because Markdown syntax is not interpreted inside raw HTML blocks such as {@code <p>...}.
 */
final class Markdown {

    private static final String SPECIAL = "&<>\"'`*_[]\\|~#!(){}+-.=:";

    private Markdown() {}

    /** Text as literal content: every character that could start markup becomes a character reference. */
    static String entities(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (char c : text.toCharArray()) {
            if (SPECIAL.indexOf(c) >= 0) {
                out.append("&#").append((int) c).append(';');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Code as inline HTML; whitespace runs are kept as they are, line breaks become spaces. */
    static String code(String text) {
        return "<code>" + entities(text.replaceAll("\\s*\\n\\s*", " ")) + "</code>";
    }
}
