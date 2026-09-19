package dev.doctize.hosting;

import dev.doctize.output.OutputDirectory;
import dev.doctize.site.Site;
import dev.doctize.site.SitePage;
import dev.doctize.text.Html;
import java.util.Optional;

/** Writes the files a hosting platform looks for next to the pages: {@code .nojekyll}, {@code CNAME}, {@code robots.txt}, {@code sitemap.xml}. */
public final class HostingFiles {

    private final OutputDirectory output;
    private final HostingSettings settings;
    private final Optional<BaseUrl> baseUrl;

    public HostingFiles(OutputDirectory output, HostingSettings settings, Optional<BaseUrl> baseUrl) {
        this.output = output;
        this.settings = settings;
        this.baseUrl = baseUrl;
    }

    public void write(Site site) {
        if (settings.githubPages()) {
            // Without this file GitHub runs the site through Jekyll, which would drop files and folders it dislikes.
            output.write(".nojekyll", "");
            if (!settings.customDomain().isEmpty()) {
                output.write("CNAME", settings.customDomain() + "\n");
            }
        }
        baseUrl.ifPresent(base -> {
            output.write("robots.txt", "User-agent: *\nAllow: /\n\nSitemap: " + base.resolve("sitemap.xml") + "\n");
            output.write("sitemap.xml", sitemap(site, base));
        });
    }

    private static String sitemap(Site site, BaseUrl base) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        entry(xml, base.value());
        for (SitePage page : site.pages()) {
            entry(xml, base.resolve(page.url()));
        }
        return xml.append("</urlset>\n").toString();
    }

    private static void entry(StringBuilder xml, String url) {
        xml.append("  <url><loc>").append(Html.escape(url)).append("</loc></url>\n");
    }
}
