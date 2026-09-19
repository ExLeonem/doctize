package dev.doctize.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.Doctize;
import dev.doctize.DoctizeException;
import dev.doctize.testing.SiteCrawler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A custom theme is layered over the built-in one, on real generated pages of every kind. */
class CustomThemeIntegrationTest {

    @TempDir
    static Path work;

    static Path project;
    static Path classes;

    @BeforeAll
    static void compile() {
        project = SampleProject.copy("sample-shop", work);
        classes = SampleProject.compile(project);
    }

    private static void write(Path root, String file, String content) throws IOException {
        Path target = root.resolve(file);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    @Test
    void overriddenPartsAppearOnEveryPageKindAndEverythingElseStaysBuiltIn() throws IOException {
        write(project, "theme-a/templates/partials/footer.mustache", "<footer id=\"custom\">{{project.name}} {{theme.options.motto}}</footer>");
        write(project, "theme-a/assets/css/theme.css", "/* replaced */ body { color: red }");
        write(project, "theme-a/assets/img/logo.svg", "<svg/>");
        write(project, "brand.css", ":root { --accent: hotpink }");
        write(project, "brand.js", "window.branded = true;");
        write(project, "themed.yml", Files.readString(project.resolve("doctize.yml")) + """
                theme:
                  path: theme-a
                  accent: "#123456"
                  extraCss: [brand.css]
                  extraJs: [brand.js]
                  options:
                    motto: Buy less
                """);
        Path site = work.resolve("site-a");

        Doctize.builder().configFile(project.resolve("themed.yml")).model(classes).output(site).build().generate();

        for (String page : new String[] {"index.html", "quick-start/start.html", "reference/shop.Cart.html", "guides/pricing-rules.html"}) {
            String html = Files.readString(site.resolve(page));
            assertThat(html).as(page).contains("<footer id=\"custom\">Sample Shop Buy less</footer>")
                    .contains("--accent: #123456")
                    .contains("assets/custom/brand.css")
                    .contains("assets/custom/brand.js")
                    .contains("class=\"topbar\"")
                    .contains("<link rel=\"stylesheet\"");
        }
        assertThat(Files.readString(site.resolve("assets/css/theme.css"))).startsWith("/* replaced */");
        assertThat(site.resolve("assets/img/logo.svg")).exists();
        assertThat(site.resolve("assets/js/search-engine.js")).exists();
        assertThat(Files.readString(site.resolve("assets/custom/brand.js"))).contains("branded");
        assertThat(SiteCrawler.check(site, "")).isEmpty();
    }

    @Test
    void aWholeTemplateCanBeReplacedAndSeesTheDocumentedData() throws IOException {
        Path theme = work.resolve("theme-b");
        write(theme, "templates/guide.mustache", "<p id=\"probe\">{{title}}|{{{bodyHtml}}}</p>");
        Path site = work.resolve("site-b");

        Doctize.builder().configFile(project.resolve("doctize.yml")).model(classes).theme(theme).output(site).build().generate();

        assertThat(Files.readString(site.resolve("setup/install.html")))
                .contains("<p id=\"probe\">Installation|")
                .contains("Requires Java 17");
    }

    @Test
    void aBrokenTemplateFailsWithAMessageThatNamesIt() throws IOException {
        Path theme = work.resolve("theme-c");
        write(theme, "templates/guide.mustache", "{{#unclosed}}");

        assertThatThrownBy(() -> Doctize.builder().configFile(project.resolve("doctize.yml")).model(classes).theme(theme).output(work.resolve("site-c")).build().generate())
                .isInstanceOf(DoctizeException.class)
                .hasMessageContaining("guide");
    }
}
