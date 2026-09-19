package dev.doctize.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks the integrity of a generated site the way a browser would experience it: every link, script,
 * stylesheet and image must lead to a file that exists, every {@code #fragment} to an element that exists,
 * and every page must have a title and exactly one top-level heading.
 */
public final class SiteCrawler {

    private static final Pattern REFERENCE = Pattern.compile("\\b(?:href|src)=\"([^\"]*)\"");
    private static final Pattern ID = Pattern.compile("\\bid=\"([^\"]+)\"");
    private static final Pattern EXTERNAL = Pattern.compile("^(?:[a-zA-Z][a-zA-Z0-9+.-]*:|//).*");

    /** One thing that is wrong with the site. */
    public record Problem(String page, String reference, String reason) {}

    private SiteCrawler() {}

    /**
     * @param siteRoot the generated output directory
     * @param absolutePrefix the path the site is served below, e.g. {@code "/doctize/"}, or {@code ""} if
     *     no page is expected to use absolute paths
     */
    public static List<Problem> check(Path siteRoot, String absolutePrefix) {
        Map<Path, String> pages = readPages(siteRoot);
        Map<Path, Set<String>> ids = new HashMap<>();
        pages.forEach((path, html) -> ids.put(path, idsOf(html)));

        List<Problem> problems = new ArrayList<>();
        pages.forEach((page, html) -> {
            String name = siteRoot.relativize(page).toString();
            if (!html.contains("<title>") || html.split("<h1[ >]", -1).length != 2) {
                problems.add(new Problem(name, "", "page needs a <title> and exactly one <h1>"));
            }
            Matcher matcher = REFERENCE.matcher(html);
            while (matcher.find()) {
                String reference = unescape(matcher.group(1));
                resolve(siteRoot, page, reference, absolutePrefix, ids).ifPresent(reason -> problems.add(new Problem(name, reference, reason)));
            }
        });
        return problems;
    }

    /** @return the reason the reference is broken, or empty if it is fine */
    private static java.util.Optional<String> resolve(
            Path root, Path page, String reference, String prefix, Map<Path, Set<String>> ids) {
        if (reference.isEmpty() || EXTERNAL.matcher(reference).matches()) {
            return java.util.Optional.empty();
        }
        int hash = reference.indexOf('#');
        String path = hash < 0 ? reference : reference.substring(0, hash);
        String fragment = hash < 0 ? "" : reference.substring(hash + 1);
        Path target;
        if (path.isEmpty()) {
            target = page;
        } else if (path.startsWith("/")) {
            if (prefix.isEmpty() || !path.startsWith(prefix)) {
                return java.util.Optional.of("absolute path outside of the site");
            }
            target = root.resolve(path.substring(prefix.length())).normalize();
        } else {
            target = page.getParent().resolve(path).normalize();
        }
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            return java.util.Optional.of("no such file");
        }
        if (!fragment.isEmpty() && ids.containsKey(target) && !ids.get(target).contains(decode(fragment))) {
            return java.util.Optional.of("no element with id '" + fragment + "'");
        }
        return java.util.Optional.empty();
    }

    private static Map<Path, String> readPages(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            Map<Path, String> pages = new HashMap<>();
            for (Path file : walk.filter(f -> f.toString().endsWith(".html")).toList()) {
                pages.put(file, Files.readString(file));
            }
            return pages;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> idsOf(String html) {
        Set<String> ids = new HashSet<>();
        Matcher matcher = ID.matcher(html);
        while (matcher.find()) {
            ids.add(unescape(matcher.group(1)));
        }
        return ids;
    }

    private static String unescape(String text) {
        return text.replace("&amp;", "&").replace("&#61;", "=").replace("&#x3D;", "=").replace("&quot;", "\"");
    }

    private static String decode(String fragment) {
        return java.net.URLDecoder.decode(fragment.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
    }
}
