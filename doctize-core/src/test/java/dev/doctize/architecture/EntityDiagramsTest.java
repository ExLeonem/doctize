package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.ArchitectureSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityDiagramsTest {

    static ClassScanner.Scan scan;

    /** The persistence annotations as JPA declares them, so that the test needs no JPA library. */
    private static Map<String, String> persistenceStubs() {
        String head = "package jakarta.persistence; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface ";
        return Map.ofEntries(
                Map.entry("jakarta.persistence.Entity", head + "Entity { String name() default \"\"; }"),
                Map.entry("jakarta.persistence.Table", head + "Table { String name() default \"\"; }"),
                Map.entry("jakarta.persistence.Id", head + "Id { }"),
                Map.entry("jakarta.persistence.Column", head + "Column { String name() default \"\"; }"),
                Map.entry("jakarta.persistence.JoinColumn", head + "JoinColumn { String name() default \"\"; }"),
                Map.entry("jakarta.persistence.Transient", head + "Transient { }"),
                Map.entry("jakarta.persistence.Embedded", head + "Embedded { }"),
                Map.entry("jakarta.persistence.MappedSuperclass", head + "MappedSuperclass { }"),
                Map.entry("jakarta.persistence.OneToMany", head + "OneToMany { String mappedBy() default \"\"; Class<?> targetEntity() default void.class; }"),
                Map.entry("jakarta.persistence.ManyToOne", head + "ManyToOne { boolean optional() default true; }"),
                Map.entry("jakarta.persistence.OneToOne", head + "OneToOne { boolean optional() default true; }"),
                Map.entry("jakarta.persistence.ManyToMany", head + "ManyToMany { }"),
                Map.entry("db.MyEntity", "package db; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface MyEntity { }"),
                Map.entry("db.MyId", "package db; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface MyId { }"));
    }

    @BeforeAll
    static void aCompiledProject(@TempDir Path work) {
        java.util.HashMap<String, String> sources = new java.util.HashMap<>(persistenceStubs());
        sources.put("db.Audited", """
                package db; import jakarta.persistence.*;
                @MappedSuperclass public abstract class Audited { @Column(name = "created_at") java.time.Instant createdAt; }
                """);
        sources.put("db.Order", """
                package db; import jakarta.persistence.*; import java.util.*; import dev.doctize.annotations.*;
                @Entity @Table(name = "orders") public class Order extends Audited {
                    @Id long id;
                    String number;
                    java.math.BigDecimal total;
                    Status status;
                    @ManyToOne(optional = false) @JoinColumn(name = "customer_ref") Customer customer;
                    @OneToMany(mappedBy = "order") List<Line> lines;
                    @ManyToMany List<Tag> tags;
                    @OneToOne Invoice invoice;
                    @Embedded Address address;
                    @Transient String cache;
                    transient int scratch;
                    static int COUNT;
                    @DiagramIgnore(types = DiagramTypes.ENTITIES) String secret;
                }
                """);
        sources.put("db.Customer", """
                package db; import jakarta.persistence.*; import java.util.*;
                @Entity public class Customer { @Id long id; String name; @OneToMany(mappedBy = "customer") List<Order> orders; }
                """);
        sources.put("db.Line", """
                package db; import jakarta.persistence.*;
                @Entity public class Line { @Id long id; @ManyToOne Order order; int qty; java.util.UUID sku; byte[] data; }
                """);
        sources.put("db.Tag", "package db; import jakarta.persistence.*; @Entity public class Tag { @Id String code; }");
        sources.put("db.Invoice", "package db; import jakarta.persistence.*; @Entity public class Invoice { @Id long id; }");
        sources.put("db.Address", "package db; public class Address { String street; }");
        sources.put("db.Status", "package db; public enum Status { NEW }");
        sources.put("db.Plain", "package db; @dev.doctize.annotations.DiagramInclude(types = dev.doctize.annotations.DiagramTypes.ENTITIES) public class Plain { long id; Plain parent; String label; }");
        sources.put("db.Ignored", "package db; @jakarta.persistence.Entity @dev.doctize.annotations.DiagramIgnore public class Ignored { }");
        sources.put("db.NotEntity", "package db; public class NotEntity { long id; }");
        sources.put("db.Custom", "package db; @MyEntity public class Custom { @MyId long key; String data; }");
        scan = ClassScanner.scanTypes(List.of(CompiledSources.compile(work, sources)));
    }

    private static DiagramResult generate(String entitiesYaml) {
        ArchitectureSettings settings = TestSettings.architecture("diagrams:\n  entities:\n" + entitiesYaml.indent(4));
        return new EntityDiagrams(new DiagramScope(settings, scan), settings.diagrams().entities()).generate();
    }

    private static String source(String entitiesYaml) {
        return generate(entitiesYaml).diagrams().get(0).source();
    }

    @Test
    void classesWithAPersistenceAnnotationAreEntitiesAndClassesWithoutOneAreNot() {
        String source = source("enabled: true");

        assertThat(source).startsWith("erDiagram\n").contains("  Order {", "  Customer {", "  Line {", "  Tag {", "  Invoice {", "  Plain {");
        assertThat(source).doesNotContain("Ignored", "NotEntity", "\n  Address", "\n  Status", "\n  Custom {");
    }

    @Test
    void columnsHaveAMappedTypeAndTheirKeys() {
        String order = source("enabled: true");

        assertThat(order).contains("long id PK", "string number", "decimal total", "enum status", "Address address", "string code PK", "int qty", "uuid sku", "blob data");
    }

    @Test
    void fieldsThatAreNotStoredAreLeftOut() {
        String source = source("enabled: true");

        assertThat(source).doesNotContain("cache", "scratch", "COUNT", "secret");
    }

    @Test
    void fieldsOfAMappedSuperclassAreColumnsOfTheEntity() {
        assertThat(source("enabled: true")).contains("timestamp createdAt");
    }

    @Test
    void aRelationFieldBecomesAForeignKeyColumnOfTheOwningSideAndNoOtherColumn() {
        String source = source("enabled: true");

        assertThat(source).contains("long customer_id FK", "long invoice_id FK", "long order_id FK");
        List<String> attributes = source.lines().filter(line -> line.startsWith("    ")).toList();
        assertThat(attributes).noneMatch(line -> line.contains("lines") || line.contains("tags") || line.contains("orders")).noneMatch(line -> line.contains("Customer customer"));
    }

    @Test
    void relationsDeclaredOnBothSidesAreDrawnOnceWithTheOwnersName() {
        String source = source("enabled: true");

        assertThat(source).contains("Customer ||--o{ Order : \"customer\"", "Order |o--o{ Line : \"order\"");
        assertThat(source.lines().filter(line -> line.contains("Customer") && line.contains("Order") && line.contains("--"))).hasSize(1);
        assertThat(source.lines().filter(line -> line.contains("Order") && line.contains("Line") && line.contains("--"))).hasSize(1);
    }

    @Test
    void cardinalitiesFollowTheAnnotations() {
        String source = source("enabled: true");

        assertThat(source).contains("Order }o--o{ Tag : \"tags\"", "Invoice |o--o| Order : \"invoice\"");
        assertThat(source).as("optional = false makes the one side mandatory").contains("Customer ||--o{ Order");
    }

    @Test
    void aFieldThatLeadsToAnEntityOfTheSameTypeIsARelationEvenWithoutAnAnnotation() {
        assertThat(source("enabled: true")).contains("Plain |o--o{ Plain : \"parent\"").doesNotContain("Plain parent");
    }

    @Test
    void aClassMarkedWithIncludeIsAnEntityEvenWithoutAPersistenceAnnotation() {
        assertThat(source("enabled: true")).contains("Plain {", "long id", "string label");
    }

    @Test
    void tableAndColumnNamesAreUsedWhenAskedFor() {
        String source = source("tableNames: true");

        assertThat(source).contains("orders {", "created_at", "customer_ref FK").doesNotContain("Order {", "createdAt", "customer_id");
        assertThat(source).contains("orders |o--o{ Line : \"order\"", "Customer ||--o{ orders : \"customer\"");
    }

    @Test
    void columnsKeysAndRelationsCanBeLeftOut() {
        String noColumns = source("columns: false");
        String noKeys = source("keys: false");
        String noRelations = source("relationships: false");

        assertThat(noColumns).doesNotContain(" {", "PK", "FK").contains("\n  Order\n", "\n  Customer\n").contains("--");
        assertThat(noKeys).doesNotContain(" PK", " FK").contains("long id", "long customer_id");
        assertThat(noRelations).doesNotContain("--").contains("Order {");
    }

    @Test
    void entitiesCanBeChosenByAnnotationOrOnlyByMark() {
        String custom = source("entityAnnotations: [db.MyEntity]\nidAnnotations: [db.MyId]");
        String marked = source("onlyMarked: true");

        assertThat(custom).contains("Custom {", "long key PK", "string data").doesNotContain("Order", "Customer");
        assertThat(marked).contains("Plain {").doesNotContain("Order", "Customer {");
    }

    @Test
    void entitiesCanBeExcludedByPattern() {
        String source = source("excludeTypes: [\"db.Tag\", \"db.I*\"]");

        assertThat(source).doesNotContain("Tag {", "Invoice {", "Tag :").contains("Order {");
        assertThat(source).as("a relation to an entity that is left out is not drawn").doesNotContain("Tag", "Invoice");
    }

    @Test
    void theItemsListEntitiesWithTheirTablesColumnsAndRelations() {
        List<String> items = generate("tableNames: true").diagrams().get(0).items();

        assertThat(items).anyMatch(item -> item.startsWith("`Order` (table `orders`)") && item.contains("columns") && item.contains("relations"));
        assertThat(items).anyMatch(item -> item.startsWith("`Tag`: 1 column, 1 relation"));
    }

    @Test
    void noEntitiesMeansNoDiagram() {
        ArchitectureSettings settings = TestSettings.architecture("diagrams:\n  entities: true");

        DiagramResult result = new EntityDiagrams(new DiagramScope(settings, new ClassScanner.Scan(List.of(), Map.of())), settings.diagrams().entities()).generate();

        assertThat(result.diagrams()).isEmpty();
    }

    @Test
    void aColumnMayBeCalledLikeAWordMermaidKnowsElsewhere() {
        String source = source("enabled: true");

        assertThat(source).contains("long id PK").doesNotContain("state_", "end_");
        assertThat(Mermaid.name("state")).isEqualTo("state");
        assertThat(Mermaid.name("end")).isEqualTo("end");
        assertThat(Mermaid.name("first name")).isEqualTo("first_name");
        assertThat(Mermaid.name("1st")).isEqualTo("_1st");
        assertThat(Mermaid.id("state")).as("a state diagram or a class diagram does need the care").isEqualTo("state_");
    }

    @Test
    void namesAreSafeIdentifiers() {
        String source = source("enabled: true");

        assertThat(source.lines().filter(line -> line.matches("  [A-Za-z_].*"))).allMatch(line -> !line.contains("$") && !line.contains("<"));
    }
}
