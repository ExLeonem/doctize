package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.architecture.ArchitectureModel.Node;
import dev.doctize.config.ArchitectureSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What {@code @DiagramIgnore} and {@code @DiagramInclude} do to the overview. */
class OverviewMarksTest {

    static ClassScanner.Scan scan;

    @BeforeAll
    static void aCompiledProject(@TempDir Path work) {
        Path classes = CompiledSources.compile(work, Map.ofEntries(
                Map.entry("m.a.A", "package m.a; public class A { m.b.B b; m.hidden.H h; m.c.Secret s; }"),
                Map.entry("m.b.B", "package m.b; public class B { }"),
                Map.entry("m.hidden.H", "package m.hidden; public class H { }"),
                Map.entry("m.hidden.Kept", "package m.hidden; @dev.doctize.annotations.DiagramInclude public class Kept { m.b.B b; }"),
                Map.entry("m.hidden.package-info", "@dev.doctize.annotations.DiagramIgnore package m.hidden;"),
                Map.entry("m.c.Secret", "package m.c; @dev.doctize.annotations.DiagramIgnore public class Secret { m.b.B b; }"),
                Map.entry("m.c.Other", "package m.c; public class Other { }"),
                Map.entry("m.x.Shown", "package m.x; public class Shown { m.c.Other o; }"),
                Map.entry("m.excluded.E", "package m.excluded; @dev.doctize.annotations.DiagramInclude public class E { m.b.B b; }"),
                Map.entry("m.excluded.F", "package m.excluded; public class F { }"),
                Map.entry("m.n.Outer", "package m.n; public class Outer { @dev.doctize.annotations.DiagramIgnore static class Inner { m.b.B b; } }"),
                Map.entry("m.o.Only", "package m.o; @dev.doctize.annotations.DiagramIgnore(types = dev.doctize.annotations.DiagramTypes.CLASSES) public class Only { m.b.B b; }"),
                Map.entry("m.p.Other", "package m.p; @dev.doctize.annotations.DiagramIgnore(types = {\"overview\"}) public class Other { m.b.B b; }")));
        scan = ClassScanner.scanTypes(List.of(classes));
    }

    private static ArchitectureModel analyze(String yaml) {
        ArchitectureSettings settings = TestSettings.architecture(yaml);
        return new ArchitectureAnalyzer(settings).analyze(scan);
    }

    private static boolean edge(ArchitectureModel model, String from, String to) {
        return model.edges().stream().anyMatch(e -> e.from().equals(from) && e.to().equals(to));
    }

    private static Node node(ArchitectureModel model, String name) {
        return model.nodes().stream().filter(n -> n.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError("no " + name + " in " + model.nodes().stream().map(Node::name).toList()));
    }

    @Test
    void anIgnoredTypeIsNoPartOfTheOverviewAndNoDependencyOnItCounts() {
        ArchitectureModel model = analyze("exclude: [\"m.excluded\"]");

        assertThat(node(model, "c").types()).as("Other is there, Secret is not").isEqualTo(1);
        assertThat(edge(model, "a", "c")).as("A refers to Secret only").isFalse();
        assertThat(edge(model, "x", "c")).isTrue();
    }

    @Test
    void whatAnIgnoredTypeDependsOnIsNotADependencyOfItsPackage() {
        ArchitectureModel model = analyze("enabled: true");

        assertThat(edge(model, "c", "b")).as("only Secret refers to B").isFalse();
    }

    @Test
    void aDependencyOnAnIgnoredTypeIsNotAnExternalLibrary() {
        ArchitectureModel model = analyze("external: true");

        assertThat(model.external()).isEmpty();
    }

    @Test
    void aMarkOnAPackageHidesItsTypesExceptTheOnesMarkedIn() {
        ArchitectureModel model = analyze("enabled: true");

        assertThat(node(model, "hidden").types()).as("Kept, not H").isEqualTo(1);
        assertThat(edge(model, "a", "hidden")).as("A refers to H only").isFalse();
        assertThat(edge(model, "hidden", "b")).isTrue();
    }

    @Test
    void aTypeThatIsIncludedIsShownAlthoughItsPackageIsExcluded() {
        ArchitectureModel model = analyze("exclude: [\"m.excluded\"]");

        assertThat(node(model, "excluded").types()).as("E is included, F is not").isEqualTo(1);
        assertThat(edge(model, "excluded", "b")).isTrue();
    }

    @Test
    void aTypeThatIsIncludedIsShownEvenOutsideTheRootPackage() {
        ArchitectureModel model = analyze("rootPackage: m.a");

        assertThat(model.nodes()).extracting(Node::name).contains("a", "m.hidden", "m.excluded");
        assertThat(node(model, "m.excluded").packages()).containsExactly("m.excluded");
    }

    @Test
    void aTypeNestedInAnIgnoredTypeIsIgnoredAndItsDependenciesDoNotLeakIntoTheOuterType() {
        ArchitectureModel model = analyze("enabled: true");

        assertThat(node(model, "n").types()).isEqualTo(1);
        assertThat(edge(model, "n", "b")).as("only the ignored Inner refers to B").isFalse();
    }

    @Test
    void aMarkThatNamesOnlyOtherKindsDoesNotHideATypeFromTheOverviewAndOneThatNamesItDoes() {
        ArchitectureModel model = analyze("enabled: true");

        assertThat(edge(model, "o", "b")).isTrue();
        assertThat(model.nodes()).extracting(Node::name).doesNotContain("p");
    }

    @Test
    void withoutMarksNothingChanges() {
        ArchitectureModel model = analyze("include: [\"m.a\", \"m.b\"]");

        assertThat(model.nodes()).extracting(Node::name).contains("a", "b");
        assertThat(edge(model, "a", "b")).isTrue();
    }
}
