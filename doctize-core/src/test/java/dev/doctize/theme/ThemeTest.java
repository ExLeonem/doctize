package dev.doctize.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.DoctizeException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeTest {

    @TempDir
    Path dir;

    @Test
    void builtInThemeProvidesEveryTemplateAndTheCoreAssets() {
        Theme theme = Theme.builtIn();

        for (String name : List.of(
                "layout", "home", "guide", "api-type", "partials/head", "partials/header", "partials/sidebar",
                "partials/toc", "partials/pager", "partials/footer", "partials/search", "partials/member",
                "partials/parameters", "partials/tags")) {
            assertThat(theme.template(name)).as(name).isNotBlank();
        }
        assertThat(theme.assets()).containsKeys(
                "css/theme.css", "js/search-engine.js", "js/search-ui.js", "js/app.js", "img/favicon.svg");
    }

    @Test
    void customThemeOverridesSingleFilesAndAddsNewAssets() throws IOException {
        Files.createDirectories(dir.resolve("templates/partials"));
        Files.writeString(dir.resolve("templates/partials/footer.mustache"), "<footer>mine</footer>");
        Files.createDirectories(dir.resolve("assets/css"));
        Files.writeString(dir.resolve("assets/css/theme.css"), "body{}");
        Files.writeString(dir.resolve("assets/extra.txt"), "x");

        Theme theme = Theme.customized(dir);

        assertThat(theme.template("partials/footer")).isEqualTo("<footer>mine</footer>");
        assertThat(theme.template("layout")).contains("<!DOCTYPE html>");
        Map<String, byte[]> assets = theme.assets();
        assertThat(new String(assets.get("css/theme.css"), StandardCharsets.UTF_8)).isEqualTo("body{}");
        assertThat(assets).containsKeys("extra.txt", "js/app.js");
    }

    @Test
    void missingTemplateAndDirectoryAreReported() {
        assertThatThrownBy(() -> Theme.builtIn().template("nope")).isInstanceOf(DoctizeException.class).hasMessageContaining("nope");
        assertThatThrownBy(() -> Theme.customized(dir.resolve("absent"))).isInstanceOf(DoctizeException.class);
    }

    @Test
    void templateEngineRendersPartialsAndEscapesByDefault() throws IOException {
        Files.createDirectories(dir.resolve("templates/partials"));
        Files.writeString(dir.resolve("templates/t.mustache"), "{{> partials/p}} {{name}} {{{raw}}} [{{missing}}]");
        Files.writeString(dir.resolve("templates/partials/p.mustache"), "P");

        String result = new TemplateEngine(Theme.customized(dir)).render("t", Map.of("name", "<b>", "raw", "<i>"));

        assertThat(result).isEqualTo("P &lt;b&gt; <i> []");
    }
}
