package dev.doctize.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.testing.SiteCrawler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A generated site must hold together: no broken link, asset or anchor, whichever way it is published. */
class SiteIntegrityIntegrationTest {

    @TempDir
    static Path work;

    static Path project;
    static Path classes;

    @BeforeAll
    static void compile() {
        project = SampleProject.copy("sample-shop", work);
        classes = SampleProject.compile(project);
    }

    @Test
    void everyLinkAssetAndAnchorOfAPlainSiteResolves() {
        Path site = work.resolve("plain");
        SampleProject.generate(project, classes, site, builder -> builder);

        assertThat(SiteCrawler.check(site, "")).isEmpty();
    }

    @Test
    void aGithubPagesSiteHoldsTogetherEvenThoughItsNotFoundPageUsesAbsolutePaths() {
        Path site = work.resolve("pages");
        SampleProject.generate(project, classes, site, builder -> builder.githubPages().baseUrl("https://example.github.io/sample-shop/"));

        assertThat(site.resolve("404.html")).exists();
        assertThat(SiteCrawler.check(site, "/sample-shop/")).isEmpty();
    }

    @Test
    void theCrawlerReallyFindsProblems() throws IOException {
        Path site = work.resolve("broken");
        SampleProject.generate(project, classes, site, builder -> builder);
        Path page = site.resolve("setup/install.html");
        Files.writeString(page, Files.readString(page).replace("</article>", "<a href=\"nope.html\">x</a><a href=\"../index.html#missing\">y</a></article>"));

        List<SiteCrawler.Problem> problems = SiteCrawler.check(site, "");

        assertThat(problems).extracting(SiteCrawler.Problem::reference).containsExactlyInAnyOrder("nope.html", "../index.html#missing");
    }

    @Test
    void generatingTwiceProducesIdenticalFiles() throws IOException {
        Path first = work.resolve("first");
        Path second = work.resolve("second");
        SampleProject.generate(project, classes, first, builder -> builder);
        SampleProject.generate(project, classes, second, builder -> builder);

        try (Stream<Path> files = Files.walk(first)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path other = second.resolve(first.relativize(file).toString());
                assertThat(other).as(first.relativize(file).toString()).exists();
                assertThat(Files.readAllBytes(other)).as(first.relativize(file).toString()).isEqualTo(Files.readAllBytes(file));
            }
        }
    }

    @Test
    void regeneratingIntoTheSameDirectoryDropsPagesThatDisappeared() throws IOException {
        Path mutableProject = SampleProject.copy("sample-shop", work.resolve("mutable"));
        Path site = work.resolve("regenerated");
        SampleProject.generate(mutableProject, classes, site, builder -> builder);
        assertThat(site.resolve("recipes/discounts.html")).exists();
        Files.delete(mutableProject.resolve("docs/recipes/discounts.md"));

        SampleProject.generate(mutableProject, classes, site, builder -> builder);

        assertThat(site.resolve("recipes")).doesNotExist();
        assertThat(SiteCrawler.check(site, "")).isEmpty();
    }
}
