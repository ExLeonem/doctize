package dev.doctize.text;

import java.text.Normalizer;
import java.util.Locale;

/** Turns free text into URL- and anchor-friendly identifiers, and back into readable titles. */
public final class Slugs {

    private Slugs() {}

    /** {@code "Quick start!"} becomes {@code "quick-start"}; never returns an empty string. */
    public static String slugify(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "section" : slug;
    }

    /** {@code "quick-start"} becomes {@code "Quick start"}; text that already reads well is kept. */
    public static String humanize(String identifier) {
        String words = identifier.replaceAll("[-_]+", " ").strip();
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
