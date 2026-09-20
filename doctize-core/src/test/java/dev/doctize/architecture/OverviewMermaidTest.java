package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.ArchitectureSettings;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OverviewMermaidTest {

    static Path classes;

    @BeforeAll
    static void aCompiledProject(@TempDir Path work) {
        classes = CompiledSources.compile(work, CompiledSources.layeredProject());
    }

    private static String source(String yaml) {
        ArchitectureSettings settings = TestSettings.architecture(yaml);
        return OverviewMermaid.source(new ArchitectureAnalyzer(settings).analyze(List.of(classes)), settings);
    }

    @Test
    void everyPartIsANodeThatSaysHowManyTypesItHas() {
        String source = source("enabled: true");

        assertThat(source).startsWith("flowchart TB\n")
                .contains("n0[\"<b>api</b><br/>1 type\"]", "<b>domain</b><br/>5 types", "<b>infra</b><br/>1 type", "<b>service</b><br/>1 type");
    }

    @Test
    void everyDependencyIsAnArrowAndAWeightAboveOneIsItsLabel() {
        String source = source("enabled: true");

        assertThat(source).contains("n0 -->|2| n1", "n0 --> n3", "n3 -->|3| n1");
        assertThat(source.lines().filter(line -> line.matches("  n\\d+ -->.*"))).hasSize(5);
    }

    @Test
    void partsAndArrowsOfACycleAreRed() {
        String source = source("enabled: true");

        assertThat(source).contains("classDef cycle stroke:#dc2626", "class n1,n2 cycle", "linkStyle ");
        String[] linkStyle = source.lines().filter(line -> line.startsWith("  linkStyle")).findFirst().orElseThrow().split(" ");
        assertThat(linkStyle[3].split(",")).as("the two arrows of the cycle").hasSize(2);
    }

    @Test
    void withoutCyclesNothingIsRed() {
        String source = source("cycles: false");

        assertThat(source).doesNotContain("linkStyle", "class n").contains("classDef cycle");
    }

    @Test
    void aBrokenRuleMakesItsArrowRed() {
        String source = source("cycles: false\nforbid:\n  - from: com.acme.domain.**\n    to: com.acme.infra.**");

        assertThat(source.lines().filter(line -> line.startsWith("  linkStyle"))).hasSize(1);
    }

    @Test
    void componentsAreMarkedAndTheDirectionCanBeLeftToRight() {
        String source = source("cycles: false\ndirection: left-right\ncomponents:\n  - name: Core\n    packages: [com.acme.domain, com.acme.service]");

        assertThat(source).startsWith("flowchart LR").contains("<b>Core</b>", "classDef component stroke:#4f46e5").containsPattern("class n\\d+ component");
    }

    @Test
    void weakDependenciesAreNotDrawnBelowTheThreshold() {
        String source = source("minReferences: 3");

        assertThat(source.lines().filter(line -> line.matches("  n\\d+ -->.*"))).hasSize(2);
    }

    @Test
    void namesCannotBreakOutOfTheirLabels() {
        ArchitectureModel model = new ArchitectureModel("", 1, 1,
                List.of(new ArchitectureModel.Node("a\"b<c>", false, "", List.of("a"), 1, -1)), List.of(), List.of(), List.of(), List.of());

        String source = OverviewMermaid.source(model, TestSettings.architecture(""));

        assertThat(source).contains("a#quot;b#lt;c#gt;").doesNotContain("a\"b");
    }
}
