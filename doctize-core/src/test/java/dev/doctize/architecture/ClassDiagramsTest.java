package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.ArchitectureSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassDiagramsTest {

    static ClassScanner.Scan scan;

    @BeforeAll
    static void aCompiledProject(@TempDir Path work) {
        Path classes = CompiledSources.compile(work, Map.ofEntries(
                Map.entry("shop.Order", """
                        package shop;
                        import dev.doctize.annotations.*;
                        import java.util.*;
                        public class Order extends Base implements Comparable<Order> {
                            private final String id;
                            private final List<Line> lines = new ArrayList<>();
                            private Customer customer;
                            private Optional<Coupon> coupon;
                            public static final int MAX = 10;
                            @DiagramIgnore private Secret secret;
                            public Order(String id) { this.id = id; }
                            public String getId() { return id; }
                            public Money total() { return new Money(0); }
                            protected void audit() {}
                            void check() {}
                            void packagePrivate() {}
                            private void helper() {}
                            @DiagramInclude private void keepMe() {}
                            public static Order of(String id) { return new Order(id); }
                            public int compareTo(Order o) { return 0; }
                            public static class Line { private Item item; private int qty; }
                        }
                        """),
                Map.entry("shop.Base", "package shop; abstract class Base { abstract void check(); }"),
                Map.entry("shop.Item", "package shop; public class Item { }"),
                Map.entry("shop.Coupon", "package shop; class Coupon { }"),
                Map.entry("shop.Secret", "package shop; @dev.doctize.annotations.DiagramIgnore class Secret { }"),
                Map.entry("shop.Payable", "package shop; public interface Payable { Money pay(Order order); }"),
                Map.entry("shop.Status", "package shop; public enum Status { NEW, PAID }"),
                Map.entry("shop.Money", "package shop; public record Money(long cents) {}"),
                Map.entry("shop.Customer", "package shop; public class Customer implements Payable { public Money pay(Order o) { return null; } public String getName() { return name; } private String name; }"),
                Map.entry("shop.web.Controller", "package shop.web; public class Controller { private shop.Order order; }"),
                Map.entry("shop.hidden.Internal", "package shop.hidden; public class Internal { }"),
                Map.entry("shop.hidden.Kept", "package shop.hidden; @dev.doctize.annotations.DiagramInclude public class Kept { }"),
                Map.entry("shop.hidden.package-info", "@dev.doctize.annotations.DiagramIgnore package shop.hidden;"),
                Map.entry("shop.only.StatesOnly", "package shop.only; @dev.doctize.annotations.DiagramIgnore(types = dev.doctize.annotations.DiagramTypes.STATES) public class StatesOnly { }")));
        scan = ClassScanner.scanTypes(List.of(classes));
    }

    private static DiagramResult generate(String classesYaml) {
        return generate("", classesYaml);
    }

    private static DiagramResult generate(String architectureYaml, String classesYaml) {
        ArchitectureSettings settings = TestSettings.architecture(architectureYaml + "\ndiagrams:\n  classes:\n" + classesYaml.indent(4));
        return new ClassDiagrams(new DiagramScope(settings, scan), settings.diagrams().classes()).generate();
    }

    private static String source(DiagramResult result, String heading) {
        return result.diagrams().stream().filter(d -> d.heading().equals(heading)).findFirst().orElseThrow(() -> new AssertionError("no diagram " + heading + " in " + headings(result))).source();
    }

    private static List<String> headings(DiagramResult result) {
        return result.diagrams().stream().map(Diagram::heading).toList();
    }

    @Test
    void thereIsOneDiagramPerPackage() {
        DiagramResult result = generate("enabled: true");

        assertThat(headings(result)).containsExactly("shop", "shop.hidden", "shop.only", "shop.web");
        assertThat(source(result, "shop")).startsWith("classDiagram\n");
    }

    @Test
    void aTypeShowsItsFieldsAndItsPublicAndProtectedMethodsByDefault() {
        String order = source(generate("enabled: true"), "shop");

        assertThat(order).contains("class Order {")
                .contains("-String id", "-List~Order.Line~ lines", "-Customer customer", "-Optional~Coupon~ coupon", "+int MAX$")
                .contains("+total() Money", "+of(String id)$ Order", "+compareTo(Order o) int")
                .doesNotContain("audit", "packagePrivate", "helper");
    }

    @Test
    void gettersAndSettersEqualsAndConstructorsAreLeftOutUnlessAskedFor() {
        String plain = source(generate("enabled: true"), "shop");
        String all = source(generate("accessors: true\nconstructors: true"), "shop");

        assertThat(plain).doesNotContain("getId", "getName", "+Order(");
        assertThat(all).contains("+getId() String", "+getName() String", "+Order(String id)");
    }

    @Test
    void theVisibilityOfMembersIsAThresholdForFieldsAndForMethods() {
        String open = source(generate("methodVisibility: private\nfieldVisibility: private"), "shop");
        String narrow = source(generate("methodVisibility: public\nfieldVisibility: public"), "shop");

        assertThat(open).contains("-helper()", "~packagePrivate()", "#audit()");
        assertThat(narrow).contains("+int MAX$").doesNotContain("-String id", "-Customer customer", "#audit()");
    }

    @Test
    void fieldsAndMethodsCanBeSwitchedOffAsAWhole() {
        String noFields = source(generate("fields: false"), "shop");
        String noMethods = source(generate("methods: false"), "shop");

        assertThat(noFields).doesNotContain("-String id", "+int MAX$").contains("+total() Money");
        assertThat(noMethods).doesNotContain("+total()").contains("-String id");
    }

    @Test
    void aMarkedMemberIsShownWhateverItsVisibilityAndAnIgnoredFieldIsNot() {
        String order = source(generate("enabled: true"), "shop");

        assertThat(order).contains("-keepMe()").doesNotContain("secret").doesNotContain("Secret");
    }

    @Test
    void kindsOfTypesAreLabelledAndTheLabelsCanBeSwitchedOff() {
        String labelled = source(generate("enabled: true"), "shop");
        String plain = source(generate("stereotypes: false"), "shop");

        assertThat(labelled).contains("<<interface>>", "<<enumeration>>", "<<record>>", "<<abstract>>");
        assertThat(plain).doesNotContain("<<");
    }

    @Test
    void enumConstantsAndRecordComponentsAreItsMembers() {
        String shop = source(generate("enabled: true"), "shop");

        assertThat(shop).containsSubsequence("class Status {", "<<enumeration>>", "NEW", "PAID", "}");
        assertThat(shop).containsSubsequence("class Money {", "<<record>>", "+long cents", "}");
        assertThat(shop).as("no values() or valueOf()").doesNotContain("valueOf", "values(");
    }

    @Test
    void nestedTypesAreShownWithTheirOuterTypeAndAnIdThatMermaidAccepts() {
        String shop = source(generate("enabled: true"), "shop");

        assertThat(shop).contains("class Order_Line[\"Order.Line\"] {", "-Item item", "-int qty");
    }

    @Test
    void relationsComeFromInheritanceInterfacesAndFields() {
        String shop = source(generate("enabled: true"), "shop");

        assertThat(shop).contains("Base <|-- Order", "Payable <|.. Customer")
                .contains("Order --> \"*\" Order_Line : lines", "Order --> Customer : customer", "Order --> \"0..1\" Coupon : coupon", "Order_Line --> Item : item");
        assertThat(shop).as("interfaces of the JDK are not part of the project").doesNotContain("Comparable <|");
    }

    @Test
    void aFieldThatIsIgnoredMakesNoRelationEither() {
        assertThat(source(generate("enabled: true"), "shop")).doesNotContain("Secret");
    }

    @Test
    void relationsCanBeSwitchedOffOneByOne() {
        String none = source(generate("inheritance: false\nassociations: false"), "shop");

        assertThat(none).doesNotContain("<|", "-->", "..>");
    }

    @Test
    void dependenciesOfMethodSignaturesAreDrawnOnRequestAndNotTwice() {
        String without = source(generate("enabled: true"), "shop");
        String with = source(generate("dependencies: true"), "shop");

        assertThat(without).doesNotContain("..>");
        assertThat(with).contains("Order ..> Money", "Payable ..> Money", "Payable ..> Order");
        assertThat(with).as("Customer already has a relation with Order").doesNotContain("Order ..> Customer");
    }

    @Test
    void typesOfOtherPackagesAreBoxesWithoutMembersWhenNeighboursAreAskedFor() {
        String alone = source(generate("enabled: true"), "shop.web");
        String withNeighbours = source(generate("neighbours: true"), "shop.web");

        assertThat(alone).contains("class Controller").doesNotContain("class Order", "-->");
        assertThat(withNeighbours).contains("Controller --> Order : order").contains("class Order").doesNotContain("-String id");
    }

    @Test
    void largePackagesAreSplitIntoParts() {
        DiagramResult result = generate("maxClasses: 4");

        assertThat(headings(result)).contains("shop (part 1 of 3)", "shop (part 2 of 3)", "shop (part 3 of 3)");
        assertThat(result.diagrams().stream().filter(d -> d.heading().startsWith("shop (part")).flatMap(d -> d.notes().stream())).allMatch(note -> note.contains("split"));
        assertThat(result.diagrams().stream().filter(d -> d.heading().startsWith("shop (part")).mapToInt(d -> d.items().size())).allMatch(count -> count <= 4);
    }

    @Test
    void allTypesCanBeOneDiagramAndComponentsCanBeTheGroups() {
        DiagramResult all = generate("groupBy: all");
        DiagramResult byComponent = generate("components:\n  - name: Sales\n    packages: [shop, shop.web]", "groupBy: component");

        assertThat(headings(all)).containsExactly("All types");
        assertThat(headings(byComponent)).containsExactly("Sales", "shop.hidden", "shop.only");
        assertThat(source(byComponent, "Sales")).contains("class Controller", "class Order {");
    }

    @Test
    void typePatternsAndTheMarkedOnlyMode() {
        DiagramResult included = generate("includeTypes: [\"shop.Order\", \"shop.Customer\"]");
        DiagramResult excluded = generate("excludeTypes: [\"shop.Money\", \"**.Base\"]");
        DiagramResult marked = generate("onlyMarked: true");

        assertThat(source(included, "shop")).contains("class Order {", "class Customer {").doesNotContain("class Money", "class Status");
        assertThat(source(excluded, "shop")).doesNotContain("class Money", "class Base").contains("class Order {");
        assertThat(headings(marked)).as("only what @DiagramInclude names").containsExactly("shop.hidden");
        assertThat(source(marked, "shop.hidden")).contains("class Kept");
    }

    @Test
    void marksBeatTheConfigurationAndAMarkOnAPackageHidesItsTypesExceptTheOnesMarkedIn() {
        DiagramResult result = generate("enabled: true");

        assertThat(source(result, "shop.hidden")).contains("class Kept").doesNotContain("Internal");
    }

    @Test
    void aMarkThatNamesOtherKindsOfDiagramDoesNotHideATypeFromClassDiagrams() {
        assertThat(source(generate("enabled: true"), "shop.only")).contains("class StatesOnly");
    }

    @Test
    void aTypeThatIsExcludedByPackageIsShownWhenMarked() {
        DiagramResult result = generate("exclude: [\"shop.hidden\"]", "enabled: true");

        assertThat(source(result, "shop.hidden")).contains("class Kept");
    }

    @Test
    void leftToRightIsAskedForWithADirectionLine() {
        assertThat(source(generate("direction: left-right"), "shop")).contains("  direction LR\n");
        assertThat(source(generate("enabled: true"), "shop")).doesNotContain("direction");
    }

    @Test
    void theItemsListTheTypesSoThatThePageCanBeRead() {
        Diagram shop = generate("enabled: true").diagrams().get(0);

        assertThat(shop.items()).contains("`Order`", "`Order.Line`", "`Payable` (interface)", "`Status` (enum)", "`Money` (record)", "`Base` (abstract class)");
        assertThat(shop.items().stream().collect(Collectors.joining())).doesNotContain("Secret");
    }

    @Test
    void theSourceContainsNothingThatMermaidReadsAsSyntaxByAccident() {
        String source = generate("methodVisibility: private\nconstructors: true\naccessors: true").diagrams().stream().map(Diagram::source).collect(Collectors.joining("\n"));

        assertThat(source).doesNotContain("<String>", "List<");
        assertThat(source.lines().filter(line -> line.contains("\"")).allMatch(line -> line.contains("[\"") || line.contains(" \"*\" ") || line.contains(" \"0..1\" "))).isTrue();
    }
}
