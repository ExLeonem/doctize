package dev.doctize.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.DoctizeException;
import dev.doctize.config.ArchitectureSettings.Direction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchitectureSettingsTest {

    @TempDir
    Path directory;

    private DoctizeConfig load(String yaml) throws IOException {
        Path file = directory.resolve("doctize.yml");
        Files.writeString(file, yaml);
        return ConfigLoader.load(file);
    }

    @Test
    void theMapIsOffByDefaultAndEverythingElseHasADefault() {
        ArchitectureSettings settings = DoctizeConfig.defaults().architecture();

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.title()).isEqualTo("Architecture");
        assertThat(settings.section()).isEqualTo("Architecture");
        assertThat(settings.order()).isEqualTo(100);
        assertThat(settings.depth()).isZero();
        assertThat(settings.rootPackage()).isEmpty();
        assertThat(settings.cycles()).isTrue();
        assertThat(settings.external()).isFalse();
        assertThat(settings.libraryDepth()).isEqualTo(2);
        assertThat(settings.minReferences()).isEqualTo(1);
        assertThat(settings.direction()).isEqualTo(Direction.TOP_DOWN);
        assertThat(settings.include()).isEmpty();
        assertThat(settings.components()).isEmpty();
    }

    @Test
    void asBlockWithOnlyEnabledKeepsTheOtherDefaults() throws IOException {
        ArchitectureSettings settings = load("architecture:\n  enabled: true\n").architecture();

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.cycles()).as("a missing boolean does not become false").isTrue();
        assertThat(settings.minReferences()).isEqualTo(1);
        assertThat(settings.order()).isEqualTo(100);
    }

    @Test
    void everySettingCanBeGiven() throws IOException {
        ArchitectureSettings settings = load("""
                architecture:
                  enabled: true
                  title: Structure
                  section: Design
                  order: 5
                  rootPackage: com.acme
                  depth: 2
                  include: [com.acme.**]
                  exclude: ["**.generated", "**.internal"]
                  components:
                    - name: Core
                      packages: [com.acme.domain, com.acme.service]
                      description: The business rules.
                    - name: Web
                      packages: [com.acme.api.**]
                  layers: [Web, Core, infra]
                  forbid:
                    - from: com.acme.domain.**
                      to: com.acme.infra.**
                      reason: The domain is independent.
                  cycles: false
                  external: true
                  libraryDepth: 3
                  minReferences: 3
                  direction: left-right
                """).architecture();

        assertThat(settings.title()).isEqualTo("Structure");
        assertThat(settings.section()).isEqualTo("Design");
        assertThat(settings.order()).isEqualTo(5);
        assertThat(settings.rootPackage()).isEqualTo("com.acme");
        assertThat(settings.depth()).isEqualTo(2);
        assertThat(settings.include()).containsExactly("com.acme.**");
        assertThat(settings.exclude()).containsExactly("**.generated", "**.internal");
        assertThat(settings.components()).hasSize(2);
        assertThat(settings.components().get(0).description()).isEqualTo("The business rules.");
        assertThat(settings.components().get(1).description()).isEmpty();
        assertThat(settings.layers()).containsExactly("Web", "Core", "infra");
        assertThat(settings.forbid()).singleElement().satisfies(rule -> {
            assertThat(rule.from()).isEqualTo("com.acme.domain.**");
            assertThat(rule.reason()).isEqualTo("The domain is independent.");
        });
        assertThat(settings.cycles()).isFalse();
        assertThat(settings.external()).isTrue();
        assertThat(settings.libraryDepth()).isEqualTo(3);
        assertThat(settings.minReferences()).isEqualTo(3);
        assertThat(settings.direction()).isEqualTo(Direction.LEFT_RIGHT);
    }

    @Test
    void aMisspelledSettingIsAnErrorLikeEverywhereInTheConfiguration() {
        assertThatThrownBy(() -> load("architecture:\n  enabeld: true\n")).isInstanceOf(DoctizeException.class).hasMessageContaining("enabeld");
    }

    @Test
    void invalidValuesAreExplained() {
        assertThatThrownBy(() -> load("architecture:\n  depth: -1\n")).hasMessageContaining("depth must not be negative");
        assertThatThrownBy(() -> load("architecture:\n  libraryDepth: 0\n")).hasMessageContaining("libraryDepth must be at least 1");
        assertThatThrownBy(() -> load("architecture:\n  minReferences: 0\n")).hasMessageContaining("minReferences must be at least 1");
        assertThatThrownBy(() -> load("architecture:\n  direction: sideways\n")).hasMessageContaining("top-down or left-right");
        assertThatThrownBy(() -> load("architecture:\n  components:\n    - name: A\n")).hasMessageContaining("needs at least one package pattern");
        assertThatThrownBy(() -> load("architecture:\n  components:\n    - packages: [a]\n")).hasMessageContaining("needs a name");
        assertThatThrownBy(() -> load("architecture:\n  forbid:\n    - from: a\n")).hasMessageContaining("needs 'from' and 'to'");
        assertThatThrownBy(() -> load("architecture:\n  components:\n    - {name: A, packages: [a]}\n    - {name: A, packages: [b]}\n")).hasMessageContaining("names A twice");
    }

    @Test
    void theDirectionIsReadInAnyCase() throws IOException {
        assertThat(load("architecture:\n  direction: Top-Down\n").architecture().direction()).isEqualTo(Direction.TOP_DOWN);
    }
}
