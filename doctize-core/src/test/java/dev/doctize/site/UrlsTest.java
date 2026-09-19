package dev.doctize.site;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlsTest {

    @Test
    void basePathLeadsBackToTheSiteRoot() {
        assertThat(Urls.basePath("index.html")).isEmpty();
        assertThat(Urls.basePath("api/A.html")).isEqualTo("../");
        assertThat(Urls.basePath("a/b/c.html")).isEqualTo("../../");
    }

    @Test
    void relativeUrlsAreSeenFromThePage() {
        assertThat(Urls.relative("guides/a.html", "api/B.html")).isEqualTo("../api/B.html");
        assertThat(Urls.relative("index.html", "api/B.html#x")).isEqualTo("api/B.html#x");
    }

    @Test
    void aLinkWithinTheSamePageIsJustTheFragment() {
        assertThat(Urls.relative("api/B.html", "api/B.html#method-x")).isEqualTo("#method-x");
        assertThat(Urls.relative("api/B.html", "api/B.html")).isEqualTo("../api/B.html");
    }

    @Test
    void siblingsAreResolvedAgainstTheDirectoryOfTheSource() {
        assertThat(Urls.resolveSibling("setup/install.md", "../guides/a.md")).isEqualTo("guides/a.md");
        assertThat(Urls.resolveSibling("index.md", "docs/b.md")).isEqualTo("docs/b.md");
        assertThat(Urls.resolveSibling("a/b/c.md", "./d.md")).isEqualTo("a/b/d.md");
    }
}
