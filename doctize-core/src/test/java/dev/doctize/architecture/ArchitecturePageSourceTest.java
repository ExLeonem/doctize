package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.ArchitectureSettings.Component;
import dev.doctize.config.ArchitectureSettings.ForbiddenDependency;
import dev.doctize.site.Page;
import dev.doctize.site.PageContent;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchitecturePageSourceTest {

    static Path classes;

    @BeforeAll
    static void aCompiledProject(@TempDir Path work) {
        classes = CompiledSources.compile(work, CompiledSources.layeredProject());
    }

    private static ArchitectureSettings settings(
            String title, String section, int order, boolean external, int minReferences, List<Component> components, List<String> layers,
            List<ForbiddenDependency> forbid, boolean cycles) {
        return new ArchitectureSettings(true, title, section, order, "", 0, null, null, components, layers, forbid, cycles, external, 2, minReferences, null);
    }

    private static ArchitecturePageSource source(ArchitectureSettings settings) {
        return new ArchitecturePageSource(settings, List.of(classes));
    }

    private static String markdown(ArchitecturePageSource source) {
        return ((PageContent.Guide) source.pages().get(0).content()).markdown();
    }

    private static ArchitectureSettings plain() {
        return settings("", "", 100, false, 1, null, null, null, true);
    }

    @Test
    void thePageIsPlacedAsConfigured() {
        Page page = source(settings("Structure", "Design", 7, false, 1, null, null, null, true)).pages().get(0);

        assertThat(page.title()).isEqualTo("Structure");
        assertThat(page.section()).isEqualTo("Design");
        assertThat(page.order()).isEqualTo(7);
        assertThat(page.slug()).isEqualTo("architecture");
        assertThat(page.keywords()).contains("architecture", "dependencies");
        assertThat(page.description()).isNotBlank();
    }

    @Test
    void theDefaultsAreAnArchitectureSectionAtTheEnd() {
        Page page = source(plain()).pages().get(0);

        assertThat(page.title()).isEqualTo("Architecture");
        assertThat(page.section()).isEqualTo("Architecture");
        assertThat(page.order()).isEqualTo(100);
    }

    @Test
    void theMarkdownHasTheMapAndTheTablesUnderHeadingsForTheTableOfContents() {
        String markdown = markdown(source(plain()));

        assertThat(markdown).contains("**8 types** in **4 packages**", "shown as **4 parts** with **5 dependencies**")
                .contains("## Map", "<svg", "## Parts", "## Dependency cycles")
                .doesNotContain("## Layers", "## Rule violations", "## Libraries");
        assertThat(markdown).contains("| `api` | `com.acme.api` | 1 | `domain`, `service` | — |");
    }

    @Test
    void componentDescriptionsGetTheirOwnColumn() {
        String markdown = markdown(source(settings("", "", 100, false, 1,
                List.of(new Component("Core", List.of("com.acme.domain", "com.acme.service"), "The business rules | and services.")), null, null, true)));

        assertThat(markdown).contains("| Part | Description |").contains("The business rules \\| and services.");
    }

    @Test
    void layersAndViolationsGetTheirSections() {
        ArchitecturePageSource source = source(settings("", "", 100, false, 1, null, List.of("api", "service", "domain", "infra"), null, true));
        String markdown = markdown(source);

        assertThat(markdown).contains("## Layers", "1. `api`: `api`", "4. `infra`: `infra`", "## Rule violations", "`infra` (layer infra) depends on `domain`".replace("`", ""));
    }

    @Test
    void whenNothingIsBrokenTheSectionsSaySo() {
        String markdown = markdown(source(settings("", "", 100, false, 1, null, null,
                List.of(new ForbiddenDependency("api", "infra", "")), false)));

        assertThat(markdown).contains("## Rule violations", "No dependency breaks a layer or a forbidden dependency.").doesNotContain("## Dependency cycles");
    }

    @Test
    void cyclesAreListed() {
        assertThat(markdown(source(plain()))).contains("## Dependency cycles", "- `domain`, `infra`");
    }

    @Test
    void librariesAreListedOnRequest() {
        assertThat(markdown(source(settings("", "", 100, true, 1, null, null, null, true)))).contains("## Libraries", "| `com.fasterxml` | `infra` | 1 |");
    }

    @Test
    void cyclesAndBrokenRulesAreWarningsSoThatABuildCanFailOnThem() {
        ArchitecturePageSource source = source(settings("", "", 100, false, 1, null, null,
                List.of(new ForbiddenDependency("com.acme.domain.**", "com.acme.infra.**", "The domain is independent.")), true));
        source.pages();

        assertThat(source.warnings()).containsExactly(
                "Architecture: dependency cycle between domain, infra.",
                "Architecture: com.acme.domain must not depend on com.acme.infra (e.g. Shortcut → JdbcOrderRepository): The domain is independent.");
    }

    @Test
    void noClassesMeansNoPageAndAWarningThatSaysWhatToDo() {
        ArchitecturePageSource source = new ArchitecturePageSource(plain(), List.of());

        assertThat(source.pages()).isEmpty();
        assertThat(source.warnings()).singleElement().asString().contains("no classes were found", "--classes");
    }

    @Test
    void theMarkdownContainsNoInlineTagThatJavadocProcessingWouldTryToResolve() {
        assertThat(markdown(source(settings("", "", 100, true, 1, null, null,
                List.of(new ForbiddenDependency("api", "service", "see {@link Foo}")), true)))).doesNotContain("{@");
    }

    @Test
    void aReasonWithoutAFullStopGetsOneAndOneWithoutDoesNotGetTwo() {
        ArchitecturePageSource source = source(settings("", "", 100, false, 1, null, null,
                List.of(new ForbiddenDependency("com.acme.domain.**", "com.acme.infra.**", "No storage in the domain")), false));
        source.pages();

        assertThat(source.warnings()).singleElement().asString().endsWith("No storage in the domain.").doesNotContain("..");
    }
}
