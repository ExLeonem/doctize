package dev.doctize.render;

import dev.doctize.markdown.LinkRewriter;
import dev.doctize.site.SitePage;
import dev.doctize.site.Urls;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Makes links in hand-written Markdown work in the generated site: links to other {@code .md}
 * files point to the pages generated from them, links to generated pages
 * ({@code ../guides/eviction.html}) are checked against the site, and other relative links point
 * to copied assets.
 */
final class GuideLinkRewriter implements LinkRewriter {

    /** Where files that are not Markdown (images ...) are published, relative to the site root. */
    static final String ASSET_DIRECTORY = "assets/content/";

    private static final Pattern LEADING_DOTS = Pattern.compile("^(?:\\.{1,2}/)+");

    private final String basePath;
    private final String sourcePath;
    private final Map<String, SitePage> pagesBySource;
    private final Map<String, SitePage> pagesByUrl;

    GuideLinkRewriter(
            String pageUrl,
            String sourcePath,
            Map<String, SitePage> pagesBySource,
            Map<String, SitePage> pagesByUrl) {
        this.basePath = Urls.basePath(pageUrl);
        this.sourcePath = sourcePath;
        this.pagesBySource = pagesBySource;
        this.pagesByUrl = pagesByUrl;
    }

    @Override
    public String link(String destination) {
        if (isAbsolute(destination)) {
            return destination;
        }
        int hash = destination.indexOf('#');
        String path = hash < 0 ? destination : destination.substring(0, hash);
        String fragment = hash < 0 ? "" : destination.substring(hash);
        if (path.endsWith(".md")) {
            SitePage target = pagesBySource.get(Urls.resolveSibling(sourcePath, path));
            return target == null ? destination : basePath + target.url() + fragment;
        }
        SitePage generated = pagesByUrl.get(LEADING_DOTS.matcher(path).replaceFirst(""));
        return generated == null ? asset(path) + fragment : basePath + generated.url() + fragment;
    }

    @Override
    public String image(String destination) {
        return isAbsolute(destination) ? destination : asset(destination);
    }

    private String asset(String path) {
        return basePath + ASSET_DIRECTORY + Urls.resolveSibling(sourcePath, path);
    }

    private static boolean isAbsolute(String destination) {
        return destination.startsWith("#")
                || destination.startsWith("/")
                || destination.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    }
}
