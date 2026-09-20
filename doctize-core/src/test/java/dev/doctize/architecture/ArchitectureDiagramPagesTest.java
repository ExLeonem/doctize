package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.ArchitectureSettings;
import dev.doctize.site.Page;
import dev.doctize.site.PageContent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pages of the architecture diagrams: which are made, where they are, and what is said when one has nothing to show. */
class ArchitectureDiagramPagesTest {

    static Path classes;

    @BeforeAll
    static void aCompiledProject(@TempDir Path work) {
        classes = CompiledSources.compile(work, Map.of(
                "demo.OrderState", """
                        package demo;
                        import dev.doctize.annotations.*;
                        @StateMachine(initial = "NEW", terminal = "PAID")
                        public enum OrderState { @Transition(to = "PAID", on = "pay") NEW, PAID }
                        """,
                "demo.Order", "package demo; @dev.doctize.annotations.DiagramInclude(types = dev.doctize.annotations.DiagramTypes.ENTITIES) public class Order { long id; String name; }",
                "demo.Service", """
                        package demo;
                        import dev.doctize.annotations.*;
                        public class Service {
                            @Flow(name = "Checkout") public void checkout() { charge(); }
                            void charge() { }
                        }
                        """));
    }

    private static ArchitecturePageSource source(String yaml) {
        return new ArchitecturePageSource(TestSettings.architecture(yaml), List.of(classes));
    }

    private static String markdown(Page page) {
        return ((PageContent.Guide) page.content()).markdown();
    }

    @Test
    void onlyTheOverviewIsMadeUntilOtherDiagramsAreSwitchedOn() {
        ArchitecturePageSource source = source("");

        assertThat(source.pages()).extracting(Page::slug).containsExactly("architecture");
        assertThat(source.warnings()).isEmpty();
    }

    @Test
    void everyKindOfDiagramThatIsOnGetsAPageOfItsOwnInTheSameSectionInOrder() {
        ArchitecturePageSource source = source("section: Design\norder: 10\ndiagrams:\n  classes: true\n  states: true\n  entities: true\n  flows: true");

        assertThat(source.pages()).extracting(Page::slug).containsExactly("architecture", "class-diagrams", "state-diagrams", "data-model", "flows");
        assertThat(source.pages()).extracting(Page::title).containsExactly("Architecture", "Class diagrams", "State diagrams", "Data model", "Flows");
        assertThat(source.pages()).extracting(Page::section).containsOnly("Design");
        assertThat(source.pages()).extracting(Page::order).containsExactly(10, 11, 12, 13, 14);
        assertThat(source.warnings()).isEmpty();
    }

    @Test
    void theTitlesOfThePagesAreTheOnesInTheSettings() {
        ArchitecturePageSource source = source("title: Overview\ndiagrams:\n  classes:\n    title: Types\n  states:\n    title: Lifecycles\n  entities:\n    title: Tables\n  flows:\n    title: Journeys");

        assertThat(source.pages()).extracting(Page::title).containsExactly("Overview", "Types", "Lifecycles", "Tables", "Journeys");
    }

    @Test
    void theOverviewCanBeSwitchedOffLeavingTheOtherDiagrams() {
        ArchitecturePageSource source = source("diagrams:\n  overview: false\n  states: true");

        assertThat(source.pages()).extracting(Page::slug).containsExactly("state-diagrams");
    }

    @Test
    void eachPageIsMarkdownWithMermaidBlocksUnderHeadingsAndAnIntroduction() {
        ArchitecturePageSource source = source("diagrams:\n  classes: true\n  states: true\n  entities: true\n  flows: true");
        List<Page> pages = source.pages();

        assertThat(markdown(pages.get(1))).startsWith("Class diagrams of the code").contains("## demo", "```mermaid\nclassDiagram\n", "- `Order`");
        assertThat(markdown(pages.get(2))).contains("## OrderState", "```mermaid\nstateDiagram-v2\n", "NEW --> PAID : pay").contains("declared at `OrderState.NEW`");
        assertThat(markdown(pages.get(3))).contains("## Entities and their relations", "```mermaid\nerDiagram\n", "Order {");
        assertThat(markdown(pages.get(4))).contains("## Checkout", "```mermaid\nflowchart TD\n", "Service.checkout()");
    }

    @Test
    void theOverviewIsMermaidByDefaultAndSvgOnRequest() {
        assertThat(markdown(source("").pages().get(0))).contains("```mermaid\nflowchart TB").doesNotContain("<svg");
        assertThat(markdown(source("diagrams:\n  overview:\n    format: svg").pages().get(0))).contains("<svg class=\"arch-map\"").doesNotContain("```mermaid");
    }

    @Test
    void aKindThatIsOnButHasNothingToShowIsAWarningThatSaysWhatToCheck(@TempDir Path work) {
        Path plain = CompiledSources.compile(work, Map.of("plain.Thing", "package plain; public class Thing { }"));
        ArchitecturePageSource source = new ArchitecturePageSource(
                TestSettings.architecture("diagrams:\n  overview: false\n  classes:\n    excludeTypes: [\"**\"]\n  states: true\n  entities: true\n  flows: true"), List.of(plain));

        assertThat(source.pages()).isEmpty();
        assertThat(source.warnings()).hasSize(4)
                .anyMatch(w -> w.startsWith("Class diagrams are switched on, but there is no type to draw") && w.contains("exclude"))
                .anyMatch(w -> w.equals("State diagrams are switched on, but no enum is marked with @StateMachine."))
                .anyMatch(w -> w.startsWith("Entity diagrams are switched on, but no entity was found") && w.contains("jakarta.persistence.Entity") && w.contains("@DiagramInclude"))
                .anyMatch(w -> w.startsWith("Flow charts are switched on, but there is no method to start from") && w.contains("@Flow") && w.contains("flows.entryPoints"));
    }

    @Test
    void flowsWithoutAnEntryPointAreAWarning() {
        Path other = classes;
        ArchitectureSettings settings = TestSettings.architecture("diagrams:\n  overview: false\n  flows:\n    entryPoints: [\"demo.Nothing#*\"]");
        ArchitecturePageSource source = new ArchitecturePageSource(settings, List.of(other));

        assertThat(source.pages()).extracting(Page::slug).containsExactly("flows");
        assertThat(source.warnings()).anyMatch(w -> w.contains("demo.Nothing#*") && w.contains("matches no method"));
    }

    @Test
    void noClassesAtAllIsOneWarningNotOnePerKind() {
        ArchitecturePageSource source = new ArchitecturePageSource(TestSettings.architecture("diagrams:\n  classes: true\n  states: true"), List.of());

        assertThat(source.pages()).isEmpty();
        assertThat(source.warnings()).singleElement().asString().contains("no classes were found", "--classes");
    }

    @Test
    void diagramsCanBeSwitchedOnInCodeWithoutTheConfigurationFile() {
        ArchitectureSettings settings = ArchitectureSettings.defaults().withDiagram(dev.doctize.config.DiagramSettings.Kind.STATES);

        assertThat(new ArchitecturePageSource(settings, List.of(classes)).pages()).extracting(Page::slug).containsExactly("architecture", "state-diagrams");
    }
}
