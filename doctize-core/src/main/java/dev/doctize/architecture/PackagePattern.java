package dev.doctize.architecture;

import java.util.regex.Pattern;

/**
 * Matches package names against patterns: a segment is matched exactly, {@code *} inside a segment stands for any
 * characters, and a segment {@code **} for any number of segments, none included.
 * So {@code com.acme.api.**} matches {@code com.acme.api} and everything below it.
 */
final class PackagePattern {

    private PackagePattern() {}

    static boolean matches(String pattern, String packageName) {
        String[] segments = pattern.strip().isEmpty() ? new String[0] : pattern.strip().split("\\.", -1);
        String[] parts = packageName.isEmpty() ? new String[0] : packageName.split("\\.", -1);
        return matches(segments, 0, parts, 0);
    }

    private static boolean matches(String[] pattern, int p, String[] parts, int q) {
        if (p == pattern.length) {
            return q == parts.length;
        }
        if (pattern[p].equals("**")) {
            for (int skip = q; skip <= parts.length; skip++) {
                if (matches(pattern, p + 1, parts, skip)) {
                    return true;
                }
            }
            return false;
        }
        return q < parts.length && segmentMatches(pattern[p], parts[q]) && matches(pattern, p + 1, parts, q + 1);
    }

    private static boolean segmentMatches(String pattern, String part) {
        if (pattern.indexOf('*') < 0) {
            return pattern.equals(part);
        }
        StringBuilder regex = new StringBuilder();
        for (String piece : pattern.split("\\*", -1)) {
            regex.append(regex.length() == 0 && !pattern.startsWith("*") ? "" : ".*").append(Pattern.quote(piece));
        }
        return part.matches(regex.toString());
    }
}
