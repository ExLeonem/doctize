package dev.doctize.themes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.Doctize;
import dev.doctize.theme.NamedTheme;
import dev.doctize.theme.Theme;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class BundledThemesTest {

    private static final Pattern VARIABLE = Pattern.compile("(--[a-z0-9-]+)\\s*:");
    /** The colours the built-in theme switches between light and dark; a theme that sets one must set it in both. */
    private static final Pattern DARK_BLOCKS = Pattern.compile(":root\\[data-theme=\"dark\"]\\s*\\{([^}]*)}|:root:not\\(\\[data-theme=\"light\"]\\)\\s*\\{([^}]*)}");

    @TempDir
    Path dir;

    static List<String> themes() {
        return new BundledThemes().themes().stream().map(NamedTheme::name).toList();
    }

    private static String stylesheet(String name) {
        Theme theme = Theme.of(name, null);
        String path = theme.stylesheets().getFirst().substring("assets/".length());
        return new String(theme.assets().get(path), StandardCharsets.UTF_8);
    }

    private static Set<String> variables(String css) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = VARIABLE.matcher(css);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    @Test
    void theThemesAreFoundOnTheClassPath() {
        assertThat(Theme.available()).extracting(NamedTheme::name)
                .containsExactly("default", "contrast", "forest", "ocean", "paper", "terminal");
        assertThat(Theme.available()).allSatisfy(theme -> assertThat(theme.description()).isNotBlank());
    }

    @ParameterizedTest
    @MethodSource("themes")
    void theDarkPaletteIsTheSameForTheToggleAndForTheSystemSetting(String name) {
        Matcher matcher = DARK_BLOCKS.matcher(stylesheet(name));
        List<String> blocks = matcher.results().map(result -> result.group(1) != null ? result.group(1) : result.group(2))
                .map(block -> block.strip().replaceAll("\\s+", " ")).toList();

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).isEqualTo(blocks.get(1));
    }

    @ParameterizedTest
    @MethodSource("themes")
    void everyColourOfTheBuiltInDarkPaletteIsReplaced(String name) {
        String builtIn = new String(Theme.builtIn().assets().get("css/theme.css"), StandardCharsets.UTF_8);
        Matcher builtInDark = DARK_BLOCKS.matcher(builtIn);
        assertThat(builtInDark.find()).isTrue();
        Matcher themeDark = DARK_BLOCKS.matcher(stylesheet(name));
        assertThat(themeDark.find()).isTrue();

        assertThat(variables(themeDark.group(1))).as("otherwise the built-in dark colours show through")
                .containsAll(variables(builtInDark.group(1)));
    }

    @ParameterizedTest
    @MethodSource("themes")
    void aSiteWithTheThemeLoadsItsStylesheet(String name) throws IOException {
        Files.createDirectories(dir.resolve("docs"));
        Files.writeString(dir.resolve("docs/guide.md"), "# Guide\n\n```java\nclass A {}\n```\n");
        Path output = dir.resolve("site");

        Doctize.builder().baseDirectory(dir).themeName(name).output(output).build().generate();

        assertThat(output.resolve("assets/css/themes/" + name + ".css")).exists();
        assertThat(Files.readString(output.resolve("guides/guide.html")))
                .contains("<link rel=\"stylesheet\" href=\"../assets/css/themes/" + name + ".css\">");
    }
}
