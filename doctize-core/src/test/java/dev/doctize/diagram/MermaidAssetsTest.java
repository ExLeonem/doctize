package dev.doctize.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.Doctize;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MermaidAssetsTest {

    @TempDir
    Path work;

    Path guides;
    Path site;

    @BeforeEach
    void aSite() throws IOException {
        guides = Files.createDirectories(work.resolve("docs"));
        site = work.resolve("site");
    }

    private void generate() {
        Doctize.builder().baseDirectory(work).guides(guides).output(site).build().generate();
    }

    @Test
    void fencesAreRecognisedInTheWaysMarkdownAllowThem() {
        assertThat(MermaidAssets.containsDiagram("text\n\n```mermaid\nflowchart TD\n```\n")).isTrue();
        assertThat(MermaidAssets.containsDiagram("~~~mermaid\nflowchart TD\n~~~")).isTrue();
        assertThat(MermaidAssets.containsDiagram("   ````mermaid title=\"x\"\nflowchart TD\n````")).isTrue();
        assertThat(MermaidAssets.containsDiagram("```  mermaid\nflowchart TD\n```")).isTrue();
    }

    @Test
    void thingsThatAreNoDiagramAreNotRecognised() {
        assertThat(MermaidAssets.containsDiagram("```java\nclass A {}\n```")).isFalse();
        assertThat(MermaidAssets.containsDiagram("Use a mermaid block: `mermaid` is the language.")).isFalse();
        assertThat(MermaidAssets.containsDiagram("```mermaidish\nx\n```")).isFalse();
        assertThat(MermaidAssets.containsDiagram("    ```mermaid\nindented code, not a fence\n    ```")).isFalse();
    }

    @Test
    void theBundledLibraryIsThereAndDefinesTheGlobalThatTheLoaderUses() {
        byte[] script = MermaidAssets.script();

        assertThat(script.length).isGreaterThan(1_000_000);
        assertThat(new String(script, script.length - 200, 200, StandardCharsets.UTF_8)).contains("globalThis[\"mermaid\"]");
    }

    @Test
    void aSiteWithADiagramGetsTheLibraryAndASiteWithoutOneDoesNot() throws IOException {
        Files.writeString(guides.resolve("plain.md"), "# Plain\n\nNo diagrams.\n");
        generate();
        assertThat(site.resolve("assets/vendor")).as("no diagram, no 5 MB script").doesNotExist();

        Files.writeString(guides.resolve("flow.md"), "# Flow\n\n```mermaid\nflowchart TD\n  A --> B\n```\n");
        generate();

        assertThat(site.resolve(MermaidAssets.SITE_PATH)).exists().isNotEmptyFile();
        assertThat(Files.readString(site.resolve("guides/flow.html"))).contains("<pre class=\"mermaid\">flowchart TD", "assets/js/diagrams.js");
        assertThat(Files.size(site.resolve(MermaidAssets.SITE_PATH))).isEqualTo(MermaidAssets.script().length);
    }

    @Test
    void everyPageLoadsTheSmallLoaderWhichDecidesWhetherThereIsAnythingToDraw() throws IOException {
        Files.writeString(guides.resolve("plain.md"), "# Plain\n");
        generate();

        assertThat(Files.readString(site.resolve("guides/plain.html"))).contains("assets/js/diagrams.js");
        assertThat(site.resolve("assets/js/diagrams.js")).exists();
        assertThat(Files.size(site.resolve("assets/js/diagrams.js"))).isLessThan(10_000);
    }
}
