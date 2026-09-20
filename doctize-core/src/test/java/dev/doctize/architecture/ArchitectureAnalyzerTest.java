package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.architecture.ArchitectureModel.Edge;
import dev.doctize.architecture.ArchitectureModel.Node;
import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.ArchitectureSettings.Component;
import dev.doctize.config.ArchitectureSettings.ForbiddenDependency;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchitectureAnalyzerTest {

    @TempDir
    Path work;

    Path classes;

    @BeforeEach
    void aCompiledProject() {
        classes = CompiledSources.compile(work, CompiledSources.layeredProject());
    }

    private ArchitectureModel analyze(ArchitectureSettings settings) {
        return new ArchitectureAnalyzer(settings).analyze(List.of(classes));
    }

    private static ArchitectureSettings with(
            String rootPackage, int depth, List<String> include, List<String> exclude, List<Component> components,
            List<String> layers, List<ForbiddenDependency> forbid, boolean cycles, boolean external) {
        return new ArchitectureSettings(true, "", "", 100, rootPackage, depth, include, exclude, components, layers, forbid,
                cycles, external, 2, 1, null);
    }

    private static ArchitectureSettings withDefaults() {
        return with("", 0, null, null, null, null, null, true, false);
    }

    private static Edge edge(ArchitectureModel model, String from, String to) {
        return model.edges().stream().filter(e -> e.from().equals(from) && e.to().equals(to)).findFirst().orElse(null);
    }

    @Test
    void theRootPackageIsTheOneAllPackagesShare() {
        ArchitectureModel model = analyze(withDefaults());

        assertThat(model.rootPackage()).isEqualTo("com.acme");
        assertThat(model.nodes()).extracting(Node::name).containsExactly("api", "domain", "infra", "service");
    }

    @Test
    void typesAreCountedWithTheirNestedTypesMergedIn() {
        ArchitectureModel model = analyze(withDefaults());

        assertThat(model.types()).as("Order.Line belongs to Order").isEqualTo(8);
        assertThat(model.nodes().stream().filter(n -> n.name().equals("domain")).findFirst().orElseThrow().types()).isEqualTo(5);
    }

    @Test
    void dependenciesAreFoundInSignaturesGenericsFieldsSuperTypesAndExceptions() {
        ArchitectureModel model = analyze(withDefaults());

        assertThat(edge(model, "api", "service")).as("a field and a constructor call").isNotNull();
        assertThat(edge(model, "api", "domain")).as("a generic return type and a throws clause").isNotNull();
        assertThat(edge(model, "service", "domain")).isNotNull();
        assertThat(edge(model, "infra", "domain")).as("implements an interface of the domain").isNotNull();
        assertThat(edge(model, "domain", "api")).isNull();
        assertThat(edge(model, "api", "infra")).isNull();
    }

    @Test
    void theWeightOfADependencyCountsPairsOfTypes() {
        ArchitectureModel model = analyze(withDefaults());

        // Controller → Order, OrderException; nothing else of the api package reaches the domain.
        assertThat(edge(model, "api", "domain").references()).isEqualTo(2);
        // OrderService → OrderRepository, Order, OrderException
        assertThat(edge(model, "service", "domain").references()).isEqualTo(3);
    }

    @Test
    void aBoxDependingOnItselfIsNoDependency() {
        assertThat(analyze(withDefaults()).edges()).noneMatch(e -> e.from().equals(e.to()));
    }

    @Test
    void cyclesAreFound() {
        ArchitectureModel model = analyze(withDefaults());

        assertThat(model.cycles()).containsExactly(List.of("domain", "infra"));
    }

    @Test
    void cyclesCanBeSwitchedOff() {
        assertThat(analyze(with("", 0, null, null, null, null, null, false, false)).cycles()).isEmpty();
    }

    @Test
    void thePackagesCanBeRestrictedToARootPackage() {
        ArchitectureModel model = analyze(with("com.acme.domain", 0, null, null, null, null, null, true, false));

        assertThat(model.nodes()).extracting(Node::name).containsExactly("domain");
        assertThat(model.edges()).isEmpty();
    }

    @Test
    void includeAndExcludePatternsSelectPackages() {
        ArchitectureModel included = analyze(with("", 0, List.of("com.acme.api", "com.acme.service"), null, null, null, null, true, false));
        ArchitectureModel excluded = analyze(with("", 0, null, List.of("**.infra"), null, null, null, true, false));

        assertThat(included.nodes()).extracting(Node::name).containsExactly("api", "service");
        assertThat(excluded.nodes()).extracting(Node::name).containsExactly("api", "domain", "service");
        assertThat(excluded.cycles()).as("the cycle went through the excluded package").isEmpty();
    }

    @Test
    void depthMergesDeeperPackagesIntoTheirAncestor() {
        Path deep = work.resolve("deep");
        Path deepClasses = CompiledSources.compile(deep, Map.of(
                "com.acme.a.b.c.One", "package com.acme.a.b.c; public class One { }",
                "com.acme.a.b.Two", "package com.acme.a.b; public class Two { Object o = new com.acme.a.b.c.One(); }",
                "com.acme.z.Three", "package com.acme.z; public class Three { Object o = new com.acme.a.b.c.One(); }"));

        ArchitectureModel model = new ArchitectureAnalyzer(with("com.acme", 1, null, null, null, null, null, true, false)).analyze(List.of(deepClasses));

        assertThat(model.nodes()).extracting(Node::name).containsExactly("a", "z");
        assertThat(model.nodes().get(0).packages()).containsExactly("com.acme.a.b", "com.acme.a.b.c");
        assertThat(edge(model, "z", "a")).isNotNull();
    }

    @Test
    void componentsGroupPackagesUnderOneName() {
        ArchitectureModel model = analyze(with("", 0, null, null,
                List.of(new Component("Core", List.of("com.acme.domain", "com.acme.service"), "The business.")),
                null, null, true, false));

        assertThat(model.nodes()).extracting(Node::name).containsExactly("Core", "api", "infra");
        Node core = model.nodes().get(0);
        assertThat(core.component()).isTrue();
        assertThat(core.description()).isEqualTo("The business.");
        assertThat(core.packages()).containsExactly("com.acme.domain", "com.acme.service");
        assertThat(edge(model, "api", "Core")).isNotNull();
        assertThat(edge(model, "Core", "infra")).as("the domain shortcut").isNotNull();
    }

    @Test
    void aDependencyAgainstTheLayersIsReported() {
        ArchitectureModel model = analyze(with("", 0, null, null, null,
                List.of("api", "service", "domain", "infra"), null, true, false));

        assertThat(model.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.rule()).isEqualTo("layer");
            assertThat(violation.from()).isEqualTo("infra");
            assertThat(violation.to()).isEqualTo("domain");
        });
    }

    @Test
    void layersMayBeNamedByPackagePattern() {
        ArchitectureModel model = analyze(with("", 0, null, null, null,
                List.of("com.acme.api", "com.acme.service", "com.acme.infra", "com.acme.domain"), null, true, false));

        assertThat(model.violations()).extracting(v -> v.from() + "→" + v.to()).containsExactly("domain→infra");
    }

    @Test
    void aForbiddenDependencyIsReportedWithAnExample() {
        ArchitectureModel model = analyze(with("", 0, null, null, null, null,
                List.of(new ForbiddenDependency("com.acme.domain.**", "com.acme.infra.**", "The domain must not know its infrastructure.")),
                true, false));

        assertThat(model.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.rule()).isEqualTo("forbidden");
            assertThat(violation.message()).contains("com.acme.domain must not depend on com.acme.infra", "Shortcut → JdbcOrderRepository",
                    "The domain must not know its infrastructure.");
            assertThat(violation.references()).isEqualTo(1);
        });
    }

    @Test
    void aForbiddenDependencyThatDoesNotExistIsNoViolation() {
        ArchitectureModel model = analyze(with("", 0, null, null, null, null,
                List.of(new ForbiddenDependency("api", "infra", "")), true, false));

        assertThat(model.violations()).isEmpty();
    }

    @Test
    void librariesAreListedWhenAskedForButTheJdkIsNot() {
        ArchitectureModel with = analyze(with("", 0, null, null, null, null, null, true, true));
        ArchitectureModel without = analyze(withDefaults());

        assertThat(with.external()).singleElement().satisfies(library -> {
            assertThat(library.name()).isEqualTo("com.fasterxml");
            assertThat(library.usedBy()).containsExactly("infra");
        });
        assertThat(without.external()).isEmpty();
    }

    @Test
    void theLibraryDepthDecidesHowMuchOfAPackageNamesALibrary() {
        ArchitectureModel two = new ArchitectureAnalyzer(new ArchitectureSettings(true, "", "", 100, "", 0, null, null, null, null, null, true, true, 2, 1, null))
                .analyze(List.of(classes));
        ArchitectureModel three = new ArchitectureAnalyzer(new ArchitectureSettings(true, "", "", 100, "", 0, null, null, null, null, null, true, true, 3, 1, null))
                .analyze(List.of(classes));
        ArchitectureModel one = new ArchitectureAnalyzer(new ArchitectureSettings(true, "", "", 100, "", 0, null, null, null, null, null, true, true, 1, 1, null))
                .analyze(List.of(classes));

        assertThat(one.external()).extracting(ArchitectureModel.ExternalLibrary::name).containsExactly("com");
        assertThat(two.external()).extracting(ArchitectureModel.ExternalLibrary::name).containsExactly("com.fasterxml");
        assertThat(three.external()).extracting(ArchitectureModel.ExternalLibrary::name).containsExactly("com.fasterxml.jackson");
    }

    @Test
    void anEmptyDirectoryHasNoArchitecture() {
        ArchitectureModel model = new ArchitectureAnalyzer(withDefaults()).analyze(List.of(work.resolve("nothing")));

        assertThat(model.isEmpty()).isTrue();
    }
}
