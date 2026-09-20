package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.ArchitectureSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StateDiagramsTest {

    static ClassScanner.Scan scan;

    @BeforeAll
    static void aCompiledProject(@TempDir Path work) {
        Path classes = CompiledSources.compile(work, Map.ofEntries(
                Map.entry("order.OrderState", """
                        package order;
                        import dev.doctize.annotations.*;
                        @StateMachine(name = "Order lifecycle", initial = "NEW", terminal = {"SHIPPED", "CANCELLED"}, description = "What happens to an order.")
                        public enum OrderState {
                            @Transition(to = "PAID", on = "pay") @Transition(to = "CANCELLED", on = "cancel") NEW,
                            @Transition(to = "SHIPPED", on = "ship") @Transition(to = "CANCELLED") PAID,
                            SHIPPED,
                            CANCELLED,
                            @DiagramIgnore(types = DiagramTypes.STATES) LEGACY
                        }
                        """),
                Map.entry("order.OrderService", """
                        package order;
                        import dev.doctize.annotations.*;
                        public class OrderService {
                            @Transition(from = {"NEW", "PAID"}, to = "CANCELLED", on = "expire", machine = OrderState.class) public void expire() {}
                            @Transition(from = "*", to = "CANCELLED", on = "fraud", machine = OrderState.class) public void fraud() {}
                            @Transition(from = "NEW", to = "NOPE", machine = OrderState.class) public void broken() {}
                            @Transition(from = "NEW", to = "PAID", machine = String.class) public void notAMachine() {}
                            @Transition(to = "PAID", machine = OrderState.class) public void noFrom() {}
                            @Transition(from = "NEW", to = "PAID") public void noMachine() {}
                            @DiagramIgnore(types = DiagramTypes.STATES) @Transition(from = "NEW", to = "PAID", on = "ignored", machine = OrderState.class) public void ignored() {}
                        }
                        """),
                Map.entry("order.Door", """
                        package order;
                        import dev.doctize.annotations.*;
                        @StateMachine(initial = "MISSING", terminal = "GONE")
                        public enum Door {
                            OPEN, CLOSED,
                            @Transition(to = "OPEN") LOCKED,
                            END;
                            @Transition(from = "CLOSED", to = "OPEN", on = "open") void open() {}
                        }
                        """),
                Map.entry("order.NotAnEnum", "package order; @dev.doctize.annotations.StateMachine public class NotAnEnum { }"),
                Map.entry("other.Hidden", "package other; @dev.doctize.annotations.StateMachine(initial = \"A\") public enum Hidden { A, B }")));
        scan = ClassScanner.scanTypes(List.of(classes));
    }

    private static DiagramResult generate(String statesYaml) {
        return generate("", statesYaml);
    }

    private static DiagramResult generate(String architectureYaml, String statesYaml) {
        ArchitectureSettings settings = TestSettings.architecture(architectureYaml + "\ndiagrams:\n  states:\n" + statesYaml.indent(4));
        return new StateDiagrams(new DiagramScope(settings, scan), settings.diagrams().states()).generate();
    }

    private static Diagram machine(DiagramResult result, String name) {
        return result.diagrams().stream().filter(d -> d.heading().equals(name)).findFirst().orElseThrow(() -> new AssertionError("no " + name + " in " + result.diagrams().stream().map(Diagram::heading).toList()));
    }

    @Test
    void everyMarkedEnumIsOneDiagramNamedAsTheAnnotationSays() {
        DiagramResult result = generate("enabled: true");

        assertThat(result.diagrams()).extracting(Diagram::heading).containsExactlyInAnyOrder("Order lifecycle", "Door", "Hidden");
        assertThat(machine(result, "Order lifecycle").description()).isEqualTo("What happens to an order.");
        assertThat(machine(result, "Door").description()).isEmpty();
    }

    @Test
    void transitionsOnConstantsStartAtThatConstant() {
        String source = machine(generate("enabled: true"), "Order lifecycle").source();

        assertThat(source).startsWith("stateDiagram-v2\n  direction LR\n").contains("NEW --> PAID : pay", "NEW --> CANCELLED : cancel", "PAID --> SHIPPED : ship")
                .contains("PAID --> CANCELLED\n");
    }

    @Test
    void transitionsOnMethodsNameWhereTheyStartAndWhichMachine() {
        String source = machine(generate("enabled: true"), "Order lifecycle").source();

        assertThat(source).contains("NEW --> CANCELLED : expire", "PAID --> CANCELLED : expire");
    }

    @Test
    void anAnyStateSourceMeansEveryStateThatIsNotTerminalAndNotTheTarget() {
        String source = machine(generate("enabled: true"), "Order lifecycle").source();

        assertThat(source).contains("NEW --> CANCELLED : fraud", "PAID --> CANCELLED : fraud");
        assertThat(source).doesNotContain("SHIPPED --> CANCELLED", "CANCELLED --> CANCELLED");
    }

    @Test
    void theStartAndTheEndAreMarked() {
        String source = machine(generate("enabled: true"), "Order lifecycle").source();

        assertThat(source).contains("[*] --> NEW", "SHIPPED --> [*]", "CANCELLED --> [*]");
    }

    @Test
    void aChangeThatIsDeclaredTwiceIsDrawnOnceAndListedTwice() {
        Diagram lifecycle = machine(generate("enabled: true"), "Order lifecycle");
        // PAID --> CANCELLED is declared on the constant (no event) and by OrderService#fraud and #expire (events).
        long drawn = lifecycle.source().lines().filter(line -> line.equals("  NEW --> CANCELLED : cancel")).count();

        assertThat(drawn).isEqualTo(1);
        assertThat(lifecycle.items().stream().filter(item -> item.startsWith("`NEW` → `CANCELLED` on cancel"))).hasSize(1);
    }

    @Test
    void aStateThatIsIgnoredIsNotInTheDiagram() {
        assertThat(machine(generate("enabled: true"), "Order lifecycle").source()).doesNotContain("LEGACY");
        assertThat(machine(generate("enabled: true"), "Order lifecycle").source()).as("a transition that is ignored").doesNotContain("ignored");
    }

    @Test
    void theEventsOnTheArrowsCanBeSwitchedOff() {
        String source = machine(generate("events: false"), "Order lifecycle").source();

        assertThat(source).contains("NEW --> PAID\n").doesNotContain(" : pay", " : cancel");
    }

    @Test
    void theDirectionIsLeftToRightUnlessToldOtherwise() {
        assertThat(machine(generate("direction: top-down"), "Order lifecycle").source()).doesNotContain("direction");
        assertThat(machine(generate("enabled: true"), "Order lifecycle").source()).contains("direction LR");
    }

    @Test
    void statesWithoutTransitionsAreStillShownAndTheReservedWordEndIsSafe() {
        String door = machine(generate("enabled: true"), "Door").source();

        assertThat(door).contains("state \"END\" as END_", "\n  END_\n", "CLOSED --> OPEN : open", "LOCKED --> OPEN\n");
        assertThat(door).as("END on its own would be read as the end of a block").doesNotContain("\n  END\n");
    }

    @Test
    void problemsInTheAnnotationsAreWarningsThatSayWhatToDo() {
        List<String> warnings = generate("enabled: true").warnings();

        assertThat(warnings).anyMatch(w -> w.contains("OrderService#broken leads to NOPE, which is no state of OrderState"));
        assertThat(warnings).anyMatch(w -> w.contains("OrderService#notAMachine") && w.contains("String is none"));
        assertThat(warnings).anyMatch(w -> w.contains("OrderService#noFrom") && w.contains("needs from"));
        assertThat(warnings).anyMatch(w -> w.contains("OrderService#noMachine") && w.contains("needs machine"));
        assertThat(warnings).anyMatch(w -> w.contains("NotAnEnum") && w.contains("not an enum"));
        assertThat(warnings).anyMatch(w -> w.contains("initial state MISSING of Door"));
        assertThat(warnings).anyMatch(w -> w.contains("terminal state GONE of Door"));
    }

    @Test
    void thePageCanListTheTransitionsAndWhereTheyAreDeclared() {
        List<String> items = machine(generate("enabled: true"), "Order lifecycle").items();

        assertThat(items).contains("State `NEW` (start)", "State `SHIPPED` (end)", "`NEW` → `PAID` on pay, declared at `OrderState.NEW`",
                "`NEW` → `CANCELLED` on expire, declared at `OrderService#expire`");
    }

    @Test
    void oddStatesAreNoted() {
        List<String> door = machine(generate("enabled: true"), "Door").notes();
        List<String> hidden = machine(generate("enabled: true"), "Hidden").notes();

        assertThat(door).anyMatch(note -> note.contains("`LOCKED` is never entered"));
        assertThat(hidden).singleElement().asString().contains("No @Transition was found");
    }

    @Test
    void enumsCanBeLeftOutByPatternOrPackage() {
        assertThat(generate("excludeTypes: [\"other.*\"]").diagrams()).extracting(Diagram::heading).doesNotContain("Hidden");
        assertThat(generate("exclude: [\"other\"]", "enabled: true").diagrams()).extracting(Diagram::heading).doesNotContain("Hidden");
        assertThat(generate("includeTypes: [\"order.Door\"]").diagrams()).extracting(Diagram::heading).containsExactly("Door");
    }
}
