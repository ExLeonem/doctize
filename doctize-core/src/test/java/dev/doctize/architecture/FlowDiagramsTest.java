package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.ArchitectureSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlowDiagramsTest {

    static ClassScanner.Scan scan;

    @BeforeAll
    static void aCompiledProject(@TempDir Path work) {
        Path classes = CompiledSources.compile(work, Map.ofEntries(
                Map.entry("app.web.OrderController", """
                        package app.web;
                        import dev.doctize.annotations.*;
                        public class OrderController {
                            private final app.core.OrderService service = new app.core.OrderService();
                            @Flow(name = "Place an order", description = "From the request to the database.")
                            public void place(String id) {
                                validate(id);
                                service.create(id);
                                hashCode();
                            }
                            private void validate(String id) { Runnable r = () -> audit(id); r.run(); }
                            private void audit(String id) { }
                            @Flow public void nothing() { }
                        }
                        """),
                Map.entry("app.core.OrderService", """
                        package app.core;
                        import dev.doctize.annotations.*;
                        public class OrderService {
                            Repository repo = new JdbcRepository();
                            Mailer mailer = new Mailer();
                            Store<Order> store = new OrderStore();
                            public void create(String id) {
                                Order order = new Order(id);
                                store.put(order);
                                repo.save(order);
                                mailer.send(order);
                                mailer.send(id);
                                price(order);
                                internal();
                                new Recursion().a();
                            }
                            @DiagramIgnore(types = DiagramTypes.FLOWS) void internal() { }
                            int price(Order order) { return order.getTotal(); }
                        }
                        """),
                Map.entry("app.core.Store", "package app.core; public interface Store<T> { void put(T item); }"),
                Map.entry("app.core.OrderStore", "package app.core; public class OrderStore implements Store<Order> { public void put(Order item) { } }"),
                Map.entry("app.core.Repository", "package app.core; public interface Repository { void save(Order order); }"),
                Map.entry("app.core.JdbcRepository", "package app.core; public class JdbcRepository implements Repository { public void save(Order order) { log(); } private void log() { } }"),
                Map.entry("app.core.InMemoryRepository", "package app.core; public class InMemoryRepository implements Repository { public void save(Order order) { } }"),
                Map.entry("app.core.Mailer", """
                        package app.core;
                        public class Mailer {
                            public void send(Order order) { deep1(); }
                            public void send(String text) { }
                            void deep1() { deep2(); }
                            void deep2() { deep3(); }
                            void deep3() { }
                        }
                        """),
                Map.entry("app.core.Order", "package app.core; public class Order { int total; public Order(String id) { } public int getTotal() { return total; } }"),
                Map.entry("app.core.Recursion", "package app.core; public class Recursion { void a() { b(); } void b() { a(); } }"),
                Map.entry("app.jobs.Nightly", "package app.jobs; public class Nightly { public void run() { new app.core.OrderService().create(\"x\"); } void helper() { } }")));
        scan = ClassScanner.scanTypes(List.of(classes));
    }

    private static DiagramResult generate(String flowsYaml) {
        return generate("", flowsYaml);
    }

    private static DiagramResult generate(String architectureYaml, String flowsYaml) {
        ArchitectureSettings settings = TestSettings.architecture(architectureYaml + "\ndiagrams:\n  flows:\n" + flowsYaml.indent(4));
        return new FlowDiagrams(new DiagramScope(settings, scan), settings.diagrams().flows()).generate();
    }

    private static Diagram flow(DiagramResult result, String heading) {
        return result.diagrams().stream().filter(d -> d.heading().equals(heading)).findFirst().orElseThrow(() -> new AssertionError("no " + heading + " in " + result.diagrams().stream().map(Diagram::heading).toList()));
    }

    private static Diagram place(String yaml) {
        return flow(generate(yaml), "Place an order");
    }

    @Test
    void everyMethodMarkedWithFlowStartsAChartNamedAsTheAnnotationSays() {
        DiagramResult result = generate("enabled: true");

        assertThat(result.diagrams()).extracting(Diagram::heading).containsExactlyInAnyOrder("Place an order", "OrderController#nothing");
        assertThat(flow(result, "Place an order").description()).isEqualTo("From the request to the database.");
    }

    @Test
    void theChartFollowsTheCallsInTheOrderTheyAreMade() {
        String source = place("enabled: true").source();

        assertThat(source).startsWith("flowchart TD\n")
                .contains("([\"OrderController.place()\"]):::entry", "[\"OrderController.validate()\"]", "[\"OrderService.create()\"]");
        assertThat(source).contains("n0 -->|1| n1", "n0 -->|2| n2");
        assertThat(source.indexOf("OrderController.validate()")).isLessThan(source.indexOf("OrderService.create()"));
    }

    @Test
    void aLambdaIsPartOfTheMethodItIsIn() {
        String source = place("enabled: true").source();

        assertThat(source).contains("OrderController.audit()").doesNotContain("lambda");
    }

    @Test
    void methodsOfTheJdkAndOfOtherLibrariesAreNotFollowed() {
        assertThat(place("enabled: true").source()).doesNotContain("hashCode", "Runnable", "run()");
    }

    @Test
    void aCallOfAnInterfaceMethodContinuesInTheClassesThatImplementIt() {
        String source = place("enabled: true").source();

        assertThat(source).contains("{{\"Repository.save()\"}}:::abstract", "\"JdbcRepository.save()\"", "\"InMemoryRepository.save()\"", "JdbcRepository.log()");
        assertThat(source.lines().filter(line -> line.contains("-.->|implemented by|"))).hasSize(3);
    }

    @Test
    void aGenericInterfaceReachesItsImplementationThroughTheBridgeTheCompilerMade() {
        String source = place("enabled: true").source();

        assertThat(source).contains("{{\"Store.put()\"}}:::abstract", "\"OrderStore.put()\"").doesNotContain("bridge");
        assertThat(source.lines().filter(line -> line.contains("OrderStore.put()")).count()).as("one node, not one for the bridge and one for the method").isEqualTo(1);
    }

    @Test
    void implementationsCanBeSwitchedOff() {
        String source = place("implementations: false").source();

        assertThat(source).contains("Repository.save()").doesNotContain("JdbcRepository", "InMemoryRepository", "implemented by");
    }

    @Test
    void methodsThatAreIgnoredAreNotStepsAndGettersAreLeftOut() {
        String source = place("enabled: true").source();

        assertThat(source).doesNotContain("internal", "getTotal");
        assertThat(place("accessors: true").source()).contains("Order.getTotal()");
    }

    @Test
    void creatingObjectsIsAStepOnlyOnRequest() {
        assertThat(place("enabled: true").source()).doesNotContain("new(");
        assertThat(place("constructors: true").source()).contains("Order.new()");
    }

    @Test
    void methodsOfTheSameNameAreToldApartByTheirParameters() {
        String source = place("enabled: true").source();

        assertThat(source).contains("Mailer.send(Order)", "Mailer.send(String)");
        assertThat(source).as("methods that are alone in their class have no parameters in the name").contains("OrderService.create()");
    }

    @Test
    void theDepthLimitsHowFarTheCallsAreFollowedAndCutMethodsAreMarked() {
        Diagram shallow = place("depth: 2");

        assertThat(shallow.source()).contains("Mailer.send(Order) …", ":::cut").doesNotContain("deep1");
        assertThat(shallow.notes()).anyMatch(note -> note.contains("deeper than 2 levels") && note.contains("marked with"));
        assertThat(place("depth: 8").source()).contains("Mailer.deep3()").doesNotContain("…");
    }

    @Test
    void theNumberOfMethodsIsLimitedAndSaysSo() {
        Diagram small = place("maxNodes: 3");

        assertThat(small.source().lines().filter(line -> line.matches("  n\\d+[(\\[{].*"))).hasSize(3);
        assertThat(small.notes()).anyMatch(note -> note.contains("more than 3 methods") && note.contains("cut off"));
    }

    @Test
    void recursionEndsWhereItStarted() {
        String source = place("depth: 8").source();

        assertThat(source.lines().filter(line -> line.contains("Recursion.a()") && line.contains("[\""))).hasSize(1);
        assertThat(source.lines().filter(line -> line.matches("  n\\d+ -->\\|\\d+\\| n\\d+"))).isNotEmpty();
    }

    @Test
    void numbersOnTheArrowsAndTheDirectionAreOptions() {
        Diagram plain = place("numbered: false\ndirection: left-right");

        assertThat(plain.source()).startsWith("flowchart LR").contains("n0 --> n1").doesNotContain("-->|");
    }

    @Test
    void typesCanBeLeftOutOfTheFlow() {
        String source = place("excludeTypes: [\"app.core.Mailer\", \"**.*Repository\"]").source();

        assertThat(source).doesNotContain("Mailer", "Repository").contains("OrderService.create()");
        assertThat(place("includeTypes: [\"app.web.*\"]").source()).doesNotContain("OrderService").contains("OrderController.audit()");
    }

    @Test
    void entryPointsCanBeNamedWithoutAnAnnotation() {
        DiagramResult result = generate("entryPoints: [\"app.jobs.*#run\"]");

        assertThat(result.diagrams()).extracting(Diagram::heading).contains("Nightly#run", "Place an order");
        assertThat(flow(result, "Nightly#run").source()).contains("Nightly.run()", "OrderService.create()");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void anEntryPointThatMatchesNothingIsAWarning() {
        assertThat(generate("entryPoints: [\"app.jobs.Nightly#missing\", \"app.nothing.*#run\"]").warnings())
                .anyMatch(warning -> warning.contains("app.jobs.Nightly#missing") && warning.contains("matches no method"))
                .anyMatch(warning -> warning.contains("app.nothing.*#run"));
    }

    @Test
    void aFlowThatCallsNothingSaysSo() {
        Diagram nothing = flow(generate("enabled: true"), "OrderController#nothing");

        assertThat(nothing.notes()).singleElement().asString().contains("calls nothing");
        assertThat(nothing.source()).contains("OrderController.nothing()").doesNotContain("-->");
    }

    @Test
    void theItemsListTheMethodsOfTheFlowSoThatThePageCanBeRead() {
        List<String> items = place("enabled: true").items();

        assertThat(items).contains("`OrderController#place` (start)", "`OrderService#create`", "`Mailer#send`", "`JdbcRepository#save`");
    }

    @Test
    void namesCannotBreakOutOfTheirLabels() {
        String source = generate("enabled: true").diagrams().stream().map(Diagram::source).reduce("", String::concat);

        assertThat(source.lines().filter(line -> line.matches("  n\\d+[(\\[{].*"))).allMatch(line -> line.chars().filter(c -> c == '"').count() == 2);
    }
}
