package dev.doctize.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTML escaping. */
public final class Html {

    private Html() {}

    private static final Pattern REFERENCE = Pattern.compile("&(?:#(\\d+)|#[xX]([0-9a-fA-F]+)|(amp|lt|gt|quot|apos));");

    /** Decodes numeric character references and the five predefined named ones. */
    public static String unescape(String html) {
        return REFERENCE.matcher(html).replaceAll(match -> {
            String decoded = match.group(1) != null
                    ? new String(Character.toChars(Integer.parseInt(match.group(1))))
                    : match.group(2) != null
                            ? new String(Character.toChars(Integer.parseInt(match.group(2), 16)))
                            : switch (match.group(3)) {
                                case "amp" -> "&";
                                case "lt" -> "<";
                                case "gt" -> ">";
                                case "quot" -> "\"";
                                default -> "'";
                            };
            return Matcher.quoteReplacement(decoded);
        });
    }

    /** Escapes text content, where quotes may stay as they are. */
    public static String escapeText(CharSequence text) {
        return text.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Escapes text for use in element content and in attribute values. */
    public static String escape(CharSequence text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
