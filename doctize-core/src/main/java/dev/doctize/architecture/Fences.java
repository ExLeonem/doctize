package dev.doctize.architecture;

/** Writes fenced code blocks that cannot be ended by what is inside them. */
final class Fences {

    private Fences() {}

    /** A fenced block: a fence one backtick longer than the longest run of backticks in the text, and at least three. */
    static String block(String language, String text) {
        int longest = 0;
        int run = 0;
        for (char c : text.toCharArray()) {
            run = c == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        String fence = "`".repeat(Math.max(3, longest + 1));
        return fence + language + "\n" + text + "\n" + fence + "\n";
    }
}
