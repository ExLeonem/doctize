package dev.doctize.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.Doctize;
import dev.doctize.GenerationReport;
import dev.doctize.config.DiagramSettings.Kind;
import dev.doctize.testing.SiteCrawler;
import dev.doctize.text.Html;
import dev.doctize.testing.TestLicenses;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** All kinds of architecture diagram, made from code that was compiled with the real compiler, with the annotations on the class path. */
class ArchitectureDiagramsIntegrationTest {

    @TempDir
    Path work;

    final TestLicenses.Ephemeral vendor = TestLicenses.Ephemeral.create();
    Path project;
    Path classes;
    Path site;

    @BeforeEach
    void aCompiledProject() {
        project = SampleProject.copy("sample-diagrams", work);
        classes = SampleProject.compile(project);
        site = work.resolve("site");
    }

    private Doctize.Builder builder() {
        Doctize.Builder builder = Doctize.builder().configFile(project.resolve("doctize.yml")).classes(classes).output(site)
                .licenseVerifier(vendor.verifier()).licenseKey(vendor.licenseText()).licenseSearch(Map.of(), work.resolve("home"));
        for (Kind kind : Kind.values()) {
            builder.diagram(kind);
        }
        return builder;
    }

    /** The Mermaid source of the diagrams of a page, as the browser reads it. */
    private String diagrams(String name) throws IOException {
        String html = Files.readString(site.resolve("architecture/" + name + ".html"));
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<pre class=\"mermaid\">(.*?)</pre>", java.util.regex.Pattern.DOTALL).matcher(html);
        StringBuilder all = new StringBuilder();
        while (matcher.find()) {
            all.append(Html.unescape(matcher.group(1))).append("\n\n");
        }
        return all.toString();
    }

    @Test
    void everyKindOfDiagramGetsItsPageAndTheSiteHasNoBrokenLinkAndTheLibraryThatDrawsThem() throws IOException {
        GenerationReport report = builder().build().generate();

        assertThat(report.warnings()).isEmpty();
        for (String name : new String[] {"architecture", "class-diagrams", "state-diagrams", "data-model", "flows"}) {
            assertThat(site.resolve("architecture/" + name + ".html")).as(name).exists();
        }
        assertThat(site.resolve("assets/vendor/mermaid.min.js")).exists();
        assertThat(SiteCrawler.check(site, "")).isEmpty();
    }

    @Test
    void classDiagramsShowTypesMembersAndRelations() throws IOException {
        builder().build().generate();
        String source = diagrams("class-diagrams");

        assertThat(source).startsWith("classDiagram\n")
                .contains("class Order {", "-List~Line~ lines", "+add(Line", "+int MAX_LINES$", "class Order_Draft[\"Order.Draft\"] {")
                .contains("<<record>>", "<<abstract>>", "Auditable <|-- Order", "Order --> \"*\" Line : lines", "Order --> Customer : customer");
        assertThat(source).as("the compiler-made members are not there").doesNotContain("$values", "lambda$", "access$");
    }

    @Test
    void theStateMachineIsDrawnFromTheAnnotationsOnTheConstantsAndTheMethods() throws IOException {
        builder().build().generate();
        String source = diagrams("state-diagrams");

        assertThat(source).contains("stateDiagram-v2", "[*] --> NEW", "NEW --> PAID : pay", "PAID --> PICKED : pick", "PICKED --> SHIPPED : ship", "NEW --> CANCELLED : cancel", "SHIPPED --> [*]");
        assertThat(source.split("NEW --> PAID : pay", -1)).as("declared on the constant and on the method, drawn once").hasSize(2);
        assertThat(Files.readString(site.resolve("architecture/state-diagrams.html"))).contains("What can happen to an order");
    }

    @Test
    void theDataModelIsDrawnFromThePersistenceAnnotations() throws IOException {
        builder().build().generate();
        String source = diagrams("data-model");

        assertThat(source).startsWith("erDiagram\n")
                .contains("Customer ||--o{ Order : \"customer\"", "Order |o--o{ Line : \"order\"", "Order }o--o{ Tag : \"tags\"", "Product |o--o{ Line : \"product\"")
                .contains("long id PK", "timestamp createdAt", "long customer_id FK", "string product_id FK", "enum state", "Address shipTo");
        assertThat(source).as("a transient field is no column, and a collection no column either").doesNotContain("cache", "lines", "tags Set");
    }

    @Test
    void theFlowFollowsTheCallsIncludingLambdasMethodReferencesAndGenericImplementations() throws IOException {
        builder().build().generate();
        String source = diagrams("flows");

        assertThat(source).contains("flowchart TD", "OrderService.place()", "Order.add()", "OrderService.validate()", "Mailer.confirm()", "Mailer.render()")
                .contains("Repository.save()", "JdbcOrders.save()", "-.->|implemented by|");
    }

    @Test
    void theDiagramsOfADocumentedProjectAreSearchable() throws IOException {
        builder().build().generate();

        assertThat(Files.readString(site.resolve("search-index.js"))).contains("Class diagrams", "Data model", "Flows", "Order lifecycle");
    }

    @Test
    void theOverviewAndTheTablesTellTheSameAsTheDiagram() throws IOException {
        builder().build().generate();
        String source = diagrams("architecture");

        assertThat(source).startsWith("flowchart TB\n").contains("<b>service</b>", "<b>domain</b>", "<b>web</b>", "<b>state</b>");
        assertThat(Files.readString(site.resolve("architecture/architecture.html"))).contains("<h2 id=\"parts\">Parts");
    }
}
