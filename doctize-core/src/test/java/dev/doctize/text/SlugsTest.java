package dev.doctize.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class SlugsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Quick start|quick-start",
        "  Hello,   World!  |hello-world",
        "Café Crème|cafe-creme",
        "1.0 Release|1.0-release",
        "already-a-slug|already-a-slug",
        "UPPER_case & more|upper-case-more",
        "###|section"
    })
    void slugifyProducesUrlSafeLowerCaseSlugs(String text, String slug) {
        assertThat(Slugs.slugify(text)).isEqualTo(slug);
    }

    @Test
    void slugifyNeverReturnsAnEmptyString() {
        assertThat(Slugs.slugify("")).isEqualTo("section");
        assertThat(Slugs.slugify("日本語")).isEqualTo("section");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"quick-start|Quick start", "how_to_guides|How to guides", "api|Api", "Already Fine|Already Fine", "-x-|X"})
    void humanizeTurnsIdentifiersIntoTitles(String identifier, String title) {
        assertThat(Slugs.humanize(identifier)).isEqualTo(title);
    }
}
