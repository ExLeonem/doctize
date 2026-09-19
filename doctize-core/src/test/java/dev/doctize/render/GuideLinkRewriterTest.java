package dev.doctize.render;

import static dev.doctize.Fixtures.guide;
import static dev.doctize.Fixtures.site;
import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.site.Site;
import dev.doctize.site.SitePage;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GuideLinkRewriterTest {

    Site site = site(guide("install", "Install", "setup", "x", "setup/install.md"), guide("start", "Start", "quick-start", "y", "quick-start/start.md"));
    Map<String, SitePage> bySource = new HashMap<>();
    Map<String, SitePage> byUrl = new HashMap<>();

    GuideLinkRewriter from(String pageUrl, String sourcePath) {
        for (SitePage page : site.pages()) {
            bySource.put(((dev.doctize.site.PageContent.Guide) page.page().content()).sourcePath(), page);
            byUrl.put(page.url(), page);
        }
        return new GuideLinkRewriter(pageUrl, sourcePath, bySource, byUrl);
    }

    @Test
    void markdownLinksPointToTheGeneratedPageAndKeepTheirFragment() {
        GuideLinkRewriter rewriter = from("quick-start/start.html", "quick-start/start.md");

        assertThat(rewriter.link("../setup/install.md")).isEqualTo("../setup/install.html");
        assertThat(rewriter.link("../setup/install.md#gradle")).isEqualTo("../setup/install.html#gradle");
    }

    @Test
    void aMarkdownLinkToAnUnknownFileIsLeftAsWritten() {
        assertThat(from("quick-start/start.html", "quick-start/start.md").link("../nope.md")).isEqualTo("../nope.md");
    }

    @Test
    void linksToGeneratedPagesByTheirUrlAreRecognised() {
        GuideLinkRewriter rewriter = from("quick-start/start.html", "quick-start/start.md");

        assertThat(rewriter.link("../setup/install.html#x")).isEqualTo("../setup/install.html#x");
        assertThat(rewriter.link("setup/install.html")).isEqualTo("../setup/install.html");
    }

    @Test
    void otherRelativeFilesAndImagesBecomeAssets() {
        GuideLinkRewriter rewriter = from("quick-start/start.html", "quick-start/start.md");

        assertThat(rewriter.link("../files/spec.pdf")).isEqualTo("../assets/content/files/spec.pdf");
        assertThat(rewriter.image("../images/a.png")).isEqualTo("../assets/content/images/a.png");
        assertThat(rewriter.image("local.png")).isEqualTo("../assets/content/quick-start/local.png");
    }

    @Test
    void absoluteAndSpecialLinksAreUntouched() {
        GuideLinkRewriter rewriter = from("quick-start/start.html", "quick-start/start.md");

        for (String link : new String[] {"https://example.org/a.md", "http://x.org", "mailto:me@x.org", "#anchor", "/root/absolute.html", "tel:123", "data:text/plain,x"}) {
            assertThat(rewriter.link(link)).as(link).isEqualTo(link);
        }
        assertThat(rewriter.image("https://example.org/a.png")).isEqualTo("https://example.org/a.png");
    }

    @Test
    void pagesOnTheRootLevelNeedNoPrefix() {
        assertThat(from("index.html", "index.md").link("setup/install.md")).isEqualTo("setup/install.html");
        assertThat(from("index.html", "index.md").image("images/a.png")).isEqualTo("assets/content/images/a.png");
    }
}
