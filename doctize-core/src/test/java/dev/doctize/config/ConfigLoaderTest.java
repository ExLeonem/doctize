package dev.doctize.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.DoctizeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @TempDir
    Path dir;

    private DoctizeConfig load(String yaml) throws IOException {
        Path file = dir.resolve("doctize.yml");
        Files.writeString(file, yaml);
        return ConfigLoader.load(file);
    }

    @Test
    void readsAllSettings() throws IOException {
        DoctizeConfig config = load("""
                project:
                  name: Demo
                  version: 2.0
                theme:
                  accent: "#0d9488"
                  extraCss: [brand.css]
                  options:
                    hero: dark
                guides: docs
                sections:
                  - id: quick-start
                    title: Quick start
                    description: Fast.
                api:
                  section: Reference
                  includeUndocumented: false
                """);

        assertThat(config.project().name()).isEqualTo("Demo");
        assertThat(config.project().version()).isEqualTo("2.0");
        assertThat(config.theme().accent()).isEqualTo("#0d9488");
        assertThat(config.theme().extraCss()).containsExactly("brand.css");
        assertThat(config.theme().options()).containsEntry("hero", "dark");
        assertThat(config.guides()).isEqualTo("docs");
        assertThat(config.sections()).containsExactly(new SectionSpec("quick-start", "Quick start", "Fast."));
        assertThat(config.api()).isEqualTo(new ApiSettings("Reference", false));
    }

    @Test
    void sectionsMayBeWrittenAsPlainTitles() throws IOException {
        DoctizeConfig config = load("sections:\n  - Quick start\n  - id: faq\n");

        assertThat(config.sections()).extracting(SectionSpec::id).containsExactly("quick-start", "faq");
        assertThat(config.sections()).extracting(SectionSpec::title).containsExactly("Quick start", "Faq");
    }

    @Test
    void emptyFileYieldsDefaults() throws IOException {
        DoctizeConfig config = load("");

        assertThat(config.defaultSection()).isEqualTo("Guides");
        assertThat(config.api().section()).isEqualTo("API Reference");
        assertThat(config.api().includeUndocumented()).isTrue();
    }

    @Test
    void unknownKeysAreRejectedSoTyposDoNotPassSilently() {
        assertThatThrownBy(() -> load("projekt:\n  name: x\n"))
                .isInstanceOf(DoctizeException.class)
                .hasMessageContaining("projekt");
    }
}
