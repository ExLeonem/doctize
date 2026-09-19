package dev.doctize.site;

import java.nio.file.Path;

/** Computes relative URLs between pages of the generated site. */
public final class Urls {

    private Urls() {}

    /** The URL prefix that leads from a page back to the site root: {@code ""}, {@code "../"} ... */
    public static String basePath(String pageUrl) {
        return "../".repeat((int) pageUrl.chars().filter(c -> c == '/').count());
    }

    /**
     * The URL of {@code target} as seen from the page at {@code fromPageUrl}. A fragment on the
     * target ({@code page.html#anchor}) is preserved; a link within the same page is just the fragment.
     */
    public static String relative(String fromPageUrl, String target) {
        int hash = target.indexOf('#');
        boolean samePage = hash >= 0 && target.substring(0, hash).equals(fromPageUrl);
        return samePage ? target.substring(hash) : basePath(fromPageUrl) + target;
    }

    /** Resolves {@code destination} against the directory of {@code sourcePath} (both use {@code /}). */
    public static String resolveSibling(String sourcePath, String destination) {
        Path parent = Path.of(sourcePath).getParent();
        Path resolved = (parent == null ? Path.of(destination) : parent.resolve(destination)).normalize();
        return resolved.toString().replace('\\', '/');
    }
}
