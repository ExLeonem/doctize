package dev.doctize.javadoc;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts the summary sentence of a description, used in listings and search results. */
public final class Summaries {

    private static final Pattern SUMMARY_TAG = Pattern.compile("\\{@summary\\s+([^}]*)}");
    private static final Set<String> ABBREVIATIONS = Set.of("e.g", "i.e", "etc", "vs", "cf", "approx");

    private Summaries() {}

    /**
     * The first sentence of the description: an explicit {@code {@summary}} if present, otherwise
     * everything up to the first period followed by whitespace, limited to the first paragraph.
     */
    public static String firstSentence(String description) {
        Matcher explicit = SUMMARY_TAG.matcher(description);
        if (explicit.find()) {
            return collapse(explicit.group(1));
        }
        String paragraph = description.strip().split("\\n\\s*\\n", 2)[0];
        return collapse(paragraph.substring(0, sentenceEnd(paragraph)));
    }

    private static int sentenceEnd(String paragraph) {
        int braces = 0;
        boolean inCode = false;
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            switch (c) {
                case '`' -> inCode = !inCode;
                case '{' -> braces++;
                case '}' -> braces = Math.max(0, braces - 1);
                case '.', '!', '?' -> {
                    if (braces == 0 && !inCode && endsSentence(paragraph, i)) {
                        return i + 1;
                    }
                }
                default -> {}
            }
        }
        return paragraph.length();
    }

    private static boolean endsSentence(String text, int index) {
        boolean followedByBreak = index + 1 == text.length() || Character.isWhitespace(text.charAt(index + 1));
        return followedByBreak && !endsWithAbbreviation(text, index);
    }

    private static boolean endsWithAbbreviation(String text, int periodIndex) {
        int start = periodIndex;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return ABBREVIATIONS.contains(text.substring(start, periodIndex).toLowerCase());
    }

    private static String collapse(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }
}
