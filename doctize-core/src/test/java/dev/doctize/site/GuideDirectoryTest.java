package dev.doctize.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.DoctizeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuideDirectoryTest {

    @TempDir
    Path root;

    private void file(String path, String content) throws IOException {
        Path target = root.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private Page page(String title) {
        return new GuideDirectory(root).pages().stream().filter(p -> p.title().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void subdirectoriesAreSectionsAndNumericPrefixesAreOrder() throws IOException {
        file("02-setup/03-install.md", "# Install\n\nText");

        Page page = page("Install");

        assertThat(page.section()).isEqualTo("setup");
        assertThat(page.slug()).isEqualTo("install");
        assertThat(page.order()).isEqualTo(3);
        assertThat(page.content()).isEqualTo(new PageContent.Guide("Text", "02-setup/03-install.md"));
    }

    @Test
    void frontMatterOverridesConventions() throws IOException {
        file("setup/x.md", """
                ---
                title: Custom
                section: Elsewhere
                order: 7
                group: How-to
                slug: my-slug
                description: Short.
                keywords: [a, b]
                ---
                Body
                """);

        Page page = page("Custom");

        assertThat(page.section()).isEqualTo("Elsewhere");
        assertThat(page.order()).isEqualTo(7);
        assertThat(page.group()).isEqualTo("How-to");
        assertThat(page.slug()).isEqualTo("my-slug");
        assertThat(page.description()).isEqualTo("Short.");
        assertThat(page.keywords()).containsExactly("a", "b");
    }

    @Test
    void titleFallsBackToFileNameAndRootFilesHaveNoSection() throws IOException {
        file("getting-started.md", "No heading here.");

        Page page = page("Getting started");

        assertThat(page.section()).isEmpty();
    }

    @Test
    void indexIsTheHomeIntroductionAndNotAPage() throws IOException {
        file("index.md", "---\nx: 1\n---\nWelcome!");
        file("a.md", "# A");

        GuideDirectory guides = new GuideDirectory(root);

        assertThat(guides.intro()).isEqualTo("Welcome!");
        assertThat(guides.pages()).extracting(Page::title).containsExactly("A");
    }

    @Test
    void otherFilesAreAssets() throws IOException {
        file("img/logo.svg", "<svg/>");
        file("a.md", "# A");

        assertThat(new GuideDirectory(root).assets()).containsExactly(root.resolve("img/logo.svg"));
    }

    @Test
    void invalidFrontMatterNamesTheFile() throws IOException {
        file("bad.md", "---\ntitle: [unclosed\n---\nx");

        assertThatThrownBy(() -> new GuideDirectory(root).pages())
                .isInstanceOf(DoctizeException.class)
                .hasMessageContaining("bad.md");
    }

    @Test
    void missingDirectoryIsReported() {
        assertThatThrownBy(() -> new GuideDirectory(root.resolve("nope")).pages())
                .isInstanceOf(DoctizeException.class)
                .hasMessageContaining("does not exist");
    }
}
