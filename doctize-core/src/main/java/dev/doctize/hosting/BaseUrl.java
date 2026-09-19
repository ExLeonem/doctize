package dev.doctize.hosting;

import dev.doctize.DoctizeException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * The address a site is published at, e.g. {@code https://acme.github.io/cache/}. The path part matters:
 * a GitHub project site lives below {@code /cache/}, not at the root of its host.
 *
 * @param value the absolute {@code http} or {@code https} URL, always ending with a slash
 */
public record BaseUrl(String value) {

    public BaseUrl {
        value = normalize(value);
    }

    /** @throws DoctizeException if the text is not an absolute {@code http(s)} URL */
    public static BaseUrl of(String text) {
        return new BaseUrl(text);
    }

    /** The path below the host, starting and ending with a slash: {@code "/"} or {@code "/cache/"}. */
    public String pathPrefix() {
        return URI.create(value).getPath();
    }

    /** The absolute URL of a page given relative to the site root, e.g. {@code guides/setup.html}. */
    public String resolve(String pageUrl) {
        return value + pageUrl;
    }

    private static String normalize(String text) {
        String trimmed = text == null ? "" : text.strip();
        try {
            URI uri = new URI(trimmed);
            boolean valid = uri.getScheme() != null
                    && (uri.getScheme().equals("http") || uri.getScheme().equals("https"))
                    && uri.getHost() != null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
            if (!valid) {
                throw new DoctizeException(invalidMessage(text));
            }
            return trimmed.endsWith("/") ? trimmed : trimmed + "/";
        } catch (URISyntaxException e) {
            throw new DoctizeException(invalidMessage(text), e);
        }
    }

    private static String invalidMessage(String text) {
        return "The base URL must be an absolute http(s) URL such as https://example.github.io/project/, but was: " + text;
    }
}
