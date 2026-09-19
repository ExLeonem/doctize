package dev.doctize.hosting;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.Fixtures;
import dev.doctize.output.OutputDirectory;
import dev.doctize.site.Site;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostingFilesTest {

    @TempDir
    Path dir;

    Site site = Fixtures.site(Fixtures.guide("a", "A", "s", "x", ""), Fixtures.guide("b&c", "B", "s", "y", ""));
    OutputDirectory output;

    @BeforeEach
    void prepare() {
        output = new OutputDirectory(dir);
        output.prepare();
    }

    @Test
    void writesNothingWithoutSettings() {
        new HostingFiles(output, HostingSettings.none(), Optional.empty()).write(site);

        assertThat(dir.resolve(".nojekyll")).doesNotExist();
        assertThat(dir.resolve("CNAME")).doesNotExist();
        assertThat(dir.resolve("robots.txt")).doesNotExist();
        assertThat(dir.resolve("sitemap.xml")).doesNotExist();
    }

    @Test
    void githubPagesAddsNoJekyllAndOnlyWithADomainCname() throws IOException {
        new HostingFiles(output, new HostingSettings("", true, ""), Optional.empty()).write(site);
        assertThat(dir.resolve(".nojekyll")).exists().isEmptyFile();
        assertThat(dir.resolve("CNAME")).doesNotExist();

        new HostingFiles(output, new HostingSettings("", true, "docs.example.org"), Optional.empty()).write(site);
        assertThat(Files.readString(dir.resolve("CNAME"))).isEqualTo("docs.example.org\n");
    }

    @Test
    void aKnownAddressAddsRobotsAndASitemapListingEveryPage() throws IOException {
        new HostingFiles(output, HostingSettings.none(), Optional.of(BaseUrl.of("https://ex.org/p"))).write(site);

        assertThat(Files.readString(dir.resolve("robots.txt"))).isEqualTo("User-agent: *\nAllow: /\n\nSitemap: https://ex.org/p/sitemap.xml\n");
        String sitemap = Files.readString(dir.resolve("sitemap.xml"));
        assertThat(sitemap).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("<loc>https://ex.org/p/</loc>")
                .contains("<loc>https://ex.org/p/s/a.html</loc>")
                .contains("<loc>https://ex.org/p/s/b-c.html</loc>");
        assertThat(sitemap.split("<loc>", -1)).hasSize(4);
        assertThat(dir.resolve(".nojekyll")).doesNotExist();
    }
}
