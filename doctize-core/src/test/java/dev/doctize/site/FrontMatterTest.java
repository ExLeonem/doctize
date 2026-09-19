package dev.doctize.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.DoctizeException;
import org.junit.jupiter.api.Test;

class FrontMatterTest {

    @Test
    void splitsTheYamlBlockFromTheBody() {
        FrontMatter matter = FrontMatter.parse("---\ntitle: Hello\norder: 3\nkeywords: [a, b]\nsingle: x\n---\n# Body\n\ntext\n", "f.md");

        assertThat(matter.string("title")).isEqualTo("Hello");
        assertThat(matter.integer("order", 100)).isEqualTo(3);
        assertThat(matter.strings("keywords")).containsExactly("a", "b");
        assertThat(matter.strings("single")).containsExactly("x");
        assertThat(matter.body()).isEqualTo("# Body\n\ntext\n");
    }

    @Test
    void aFileWithoutFrontMatterIsAllBody() {
        FrontMatter matter = FrontMatter.parse("# Just a heading\n\n--- not front matter ---\n", "f.md");

        assertThat(matter.values()).isEmpty();
        assertThat(matter.body()).startsWith("# Just a heading");
    }

    @Test
    void missingKeysFallBackToDefaults() {
        FrontMatter matter = FrontMatter.parse("---\nother: 1\n---\nx", "f.md");

        assertThat(matter.string("title")).isEmpty();
        assertThat(matter.integer("order", 42)).isEqualTo(42);
        assertThat(matter.strings("keywords")).isEmpty();
    }

    @Test
    void anEmptyBlockIsAllowedAndWindowsLineEndingsAreHandled() {
        assertThat(FrontMatter.parse("---\n---\nbody", "f.md").body()).isEqualTo("body");
        FrontMatter crlf = FrontMatter.parse("---\r\ntitle: T\r\n---\r\nbody", "f.md");
        assertThat(crlf.string("title")).isEqualTo("T");
        assertThat(crlf.body()).isEqualTo("body");
    }

    @Test
    void numbersInTitlesAreKeptAsText() {
        assertThat(FrontMatter.parse("---\ntitle: 2024\n---\nx", "f.md").string("title")).isEqualTo("2024");
    }

    @Test
    void invalidYamlIsReportedWithTheFileName() {
        assertThatThrownBy(() -> FrontMatter.parse("---\ntitle: [oops\n---\nx", "docs/bad.md"))
                .isInstanceOf(DoctizeException.class)
                .hasMessageContaining("docs/bad.md");
    }
}
