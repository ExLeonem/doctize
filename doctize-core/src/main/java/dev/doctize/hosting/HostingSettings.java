package dev.doctize.hosting;

import dev.doctize.DoctizeException;
import dev.doctize.annotations.Doc;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where and how the site is published.
 *
 * <p>The site works wherever it is put, because all its links are relative. Telling Doctize where it will
 * be published unlocks what needs absolute addresses: a {@code 404.html} that works at any depth,
 * {@code sitemap.xml}, {@code robots.txt} and canonical links. The address is the first of these that is set:
 *
 * <ol>
 *   <li>{@code baseUrl}
 *   <li>{@code customDomain}, as {@code https://<domain>/}
 *   <li>with {@code githubPages}: derived from {@code project.repository}, so
 *       {@code https://github.com/acme/cache} becomes {@code https://acme.github.io/cache/}
 * </ol>
 *
 * @param baseUrl the address the site is published at, may be empty
 * @param githubPages whether to prepare the output for GitHub Pages: adds {@code .nojekyll} (and {@code CNAME}
 *     for a custom domain) so that GitHub publishes the files exactly as generated
 * @param customDomain a custom domain for GitHub Pages such as {@code docs.acme.dev}, may be empty
 */
@Doc(group = "Configuration", order = 26)
public record HostingSettings(String baseUrl, boolean githubPages, String customDomain) {

    private static final Pattern DOMAIN = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?");
    private static final Pattern GITHUB_REPOSITORY = Pattern.compile("https?://(?:www\\.)?github\\.com/([^/\\s]+)/([^/\\s#?]+?)(?:\\.git)?/?");

    public HostingSettings {
        baseUrl = baseUrl == null ? "" : baseUrl.strip();
        customDomain = customDomain == null ? "" : customDomain.strip();
        if (!customDomain.isEmpty() && !DOMAIN.matcher(customDomain).matches()) {
            throw new DoctizeException("The custom domain must be a host name such as docs.example.com, but was: " + customDomain);
        }
    }

    public static HostingSettings none() {
        return new HostingSettings("", false, "");
    }

    /**
     * Settings given on top of these ones: a non-empty {@code baseUrl} or {@code customDomain} replaces the
     * current value, and {@code githubPages} is on if either says so.
     */
    public HostingSettings overriddenBy(String baseUrl, Boolean githubPages, String customDomain) {
        return new HostingSettings(
                baseUrl == null || baseUrl.isBlank() ? this.baseUrl : baseUrl,
                githubPages == null ? this.githubPages : githubPages,
                customDomain == null || customDomain.isBlank() ? this.customDomain : customDomain);
    }

    /**
     * @param repositoryUrl the {@code project.repository} setting, used to derive a GitHub Pages address
     * @return the address of the site, or empty if it cannot be determined
     */
    public Optional<BaseUrl> resolveBaseUrl(String repositoryUrl) {
        if (!baseUrl.isEmpty()) {
            return Optional.of(BaseUrl.of(baseUrl));
        }
        if (!customDomain.isEmpty()) {
            return Optional.of(BaseUrl.of("https://" + customDomain + "/"));
        }
        return githubPages ? githubPagesAddress(repositoryUrl) : Optional.empty();
    }

    private static Optional<BaseUrl> githubPagesAddress(String repository) {
        Matcher matcher = GITHUB_REPOSITORY.matcher(repository.strip());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String owner = matcher.group(1).toLowerCase(Locale.ROOT);
        String name = matcher.group(2);
        boolean userSite = name.equalsIgnoreCase(owner + ".github.io");
        return Optional.of(BaseUrl.of("https://" + owner + ".github.io/" + (userSite ? "" : name + "/")));
    }
}
