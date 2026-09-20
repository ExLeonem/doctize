package dev.doctize.architecture;

import dev.doctize.architecture.Marks.Mark;
import dev.doctize.architecture.TypeInfo.AnnotationInfo;
import dev.doctize.architecture.TypeInfo.FieldInfo;
import dev.doctize.config.DiagramSettings.EntityDiagram;
import dev.doctize.config.DiagramSettings.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Makes the entity-relationship diagram of the database model. Entities are the classes with a persistence annotation (JPA, by
 * default, or the ones the configuration names), and the classes marked with {@code @DiagramInclude}. Their fields are the columns,
 * and fields that lead to other entities are the relations.
 */
final class EntityDiagrams {

    private static final List<String> PERSISTENCE_PACKAGES = List.of("jakarta.persistence.", "javax.persistence.");
    private static final Set<String> COLLECTIONS = Set.of(
            "java.util.List", "java.util.Set", "java.util.Collection", "java.util.SortedSet", "java.util.ArrayList", "java.util.HashSet",
            "java.util.LinkedHashSet", "java.util.TreeSet", "java.lang.Iterable");
    private static final Set<String> TIMESTAMPS = Set.of(
            "java.time.LocalDateTime", "java.time.Instant", "java.time.OffsetDateTime", "java.time.ZonedDateTime", "java.sql.Timestamp", "java.util.Date");

    private enum Cardinality { ONE_TO_MANY, MANY_TO_ONE, ONE_TO_ONE, MANY_TO_MANY }

    private record Relationship(String entity, String target, Cardinality kind, String label, boolean optional, boolean inverse) {}

    private record Column(String type, String name, String keys) {}

    private final DiagramScope scope;
    private final EntityDiagram settings;
    private final Map<String, TypeInfo> entities = new LinkedHashMap<>();

    EntityDiagrams(DiagramScope scope, EntityDiagram settings) {
        this.scope = scope;
        this.settings = settings;
    }

    DiagramResult generate() {
        for (TypeInfo type : scope.types()) {
            if (isEntity(type)) {
                entities.put(type.name(), type);
            }
        }
        if (entities.isEmpty()) {
            return new DiagramResult(List.of(), List.of());
        }
        Map<String, List<Column>> columns = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, List<Relationship>> relationships = new LinkedHashMap<>();
        for (TypeInfo entity : entities.values()) {
            names.put(entity.name(), entityName(entity));
        }
        for (TypeInfo entity : entities.values()) {
            List<Column> entityColumns = new ArrayList<>();
            List<Relationship> entityRelationships = new ArrayList<>();
            for (FieldInfo field : fieldsOf(entity)) {
                readField(field, entity, entityColumns, entityRelationships);
            }
            columns.put(entity.name(), entityColumns);
            relationships.put(entity.name(), entityRelationships);
        }
        List<String> items = new ArrayList<>();
        StringBuilder source = new StringBuilder("erDiagram\n");
        List<Relationship> merged = settings.relationships() ? merge(relationships) : List.of();
        for (Relationship relationship : merged) {
            source.append("  ").append(line(relationship, names)).append('\n');
        }
        Map<String, Integer> relationCounts = new TreeMap<>();
        merged.forEach(r -> {
            relationCounts.merge(r.entity(), 1, Integer::sum);
            if (!r.entity().equals(r.target())) {
                relationCounts.merge(r.target(), 1, Integer::sum);
            }
        });
        for (TypeInfo entity : entities.values()) {
            String name = names.get(entity.name());
            List<Column> entityColumns = columns.get(entity.name());
            if (settings.columns() && !entityColumns.isEmpty()) {
                source.append("  ").append(name).append(" {\n");
                for (Column column : entityColumns) {
                    source.append("    ").append(column.type()).append(' ').append(column.name())
                            .append(column.keys().isEmpty() || !settings.keys() ? "" : " " + column.keys()).append('\n');
                }
                source.append("  }\n");
            } else {
                // An entity without attributes is declared by its name alone, so that it is in the diagram whatever its relations are.
                source.append("  ").append(name).append('\n');
            }
            items.add("`" + entity.displayName() + "`" + (name.equals(entity.displayName()) ? "" : " (table `" + name + "`)") + ": " + entityColumns.size()
                    + " column" + (entityColumns.size() == 1 ? "" : "s") + ", " + relationCounts.getOrDefault(entity.name(), 0) + " relation"
                    + (relationCounts.getOrDefault(entity.name(), 0) == 1 ? "" : "s"));
        }
        List<String> notes = entities.size() > 30
                ? List.of("There are " + entities.size() + " entities, which is many for one diagram. Leave some out with excludeTypes, or mark the ones that matter with @DiagramInclude and set onlyMarked.")
                : List.of();
        return new DiagramResult(List.of(new Diagram("Entities and their relations", "", source.toString().stripTrailing(), items, notes)), List.of());
    }

    // ---- what is an entity

    private boolean isEntity(TypeInfo type) {
        Mark mark = scope.mark(type, Kind.ENTITIES);
        if (!scope.inScope(type, Kind.ENTITIES)) {
            return false;
        }
        if (mark.included()) {
            return true;
        }
        if (settings.onlyMarked() || type.annotations().stream().noneMatch(annotation -> settings.entityAnnotations().contains(annotation.type()))) {
            return false;
        }
        return DiagramScope.passesTypePatterns(type, settings.includeTypes(), settings.excludeTypes());
    }

    private String entityName(TypeInfo type) {
        if (settings.tableNames()) {
            Optional<String> table = persistence(type.annotations(), "Table").map(annotation -> annotation.string("name", "")).filter(name -> !name.isBlank());
            if (table.isPresent()) {
                return Mermaid.name(table.get());
            }
        }
        return Mermaid.name(type.displayName().replace('.', '_'));
    }

    /** The fields of an entity, and of the super classes whose fields are part of its table. */
    private List<FieldInfo> fieldsOf(TypeInfo entity) {
        List<FieldInfo> fields = new ArrayList<>();
        TypeInfo current = entity;
        Set<String> seen = new LinkedHashSet<>();
        while (current != null && seen.add(current.name())) {
            if (current != entity && persistence(current.annotations(), "MappedSuperclass").isEmpty()) {
                break;
            }
            fields.addAll(0, current.fields());
            current = current.superName() == null ? null : scope.type(current.superName()).orElse(null);
        }
        return fields;
    }

    private static Optional<AnnotationInfo> persistence(List<AnnotationInfo> annotations, String simpleName) {
        return annotations.stream().filter(annotation -> PERSISTENCE_PACKAGES.stream().anyMatch(prefix -> annotation.type().equals(prefix + simpleName))).findFirst();
    }

    // ---- columns and relations

    private void readField(FieldInfo field, TypeInfo entity, List<Column> columns, List<Relationship> relationships) {
        if (field.isStatic() || field.isSynthetic() || field.isEnumConstant() || (field.flags() & TypeInfo.TRANSIENT_OR_VARARGS) != 0
                || persistence(field.annotations(), "Transient").isPresent() || Marks.of(field.annotations(), Kind.ENTITIES).ignored()) {
            return;
        }
        TypeRef type = field.type();
        Optional<Cardinality> declared = relationKind(field);
        String target = target(field, type);
        boolean toEntity = target != null && entities.containsKey(target);
        if (toEntity) {
            Cardinality kind = declared.orElse(isCollection(type) ? Cardinality.ONE_TO_MANY : Cardinality.MANY_TO_ONE);
            Optional<AnnotationInfo> annotation = relationAnnotation(field);
            boolean inverse = annotation.map(a -> !a.string("mappedBy", "").isBlank()).orElse(false);
            boolean optional = annotation.map(a -> !Boolean.FALSE.equals(a.values().get("optional"))).orElse(true);
            relationships.add(new Relationship(entity.name(), target, kind, field.name(), optional, inverse));
            if ((kind == Cardinality.MANY_TO_ONE || kind == Cardinality.ONE_TO_ONE) && !inverse) {
                columns.add(new Column(idType(entities.get(target)), foreignKeyName(field), "FK"));
            }
            return;
        }
        if (declared.isPresent()) {
            return;
        }
        String keys = field.annotations().stream().anyMatch(annotation -> settings.idAnnotations().contains(annotation.type())) ? "PK" : "";
        columns.add(new Column(dbType(type), columnName(field), keys));
    }

    private Optional<Cardinality> relationKind(FieldInfo field) {
        return relationAnnotation(field).map(annotation -> switch (annotation.type().substring(annotation.type().lastIndexOf('.') + 1)) {
            case "OneToMany" -> Cardinality.ONE_TO_MANY;
            case "ManyToOne" -> Cardinality.MANY_TO_ONE;
            case "OneToOne" -> Cardinality.ONE_TO_ONE;
            default -> Cardinality.MANY_TO_MANY;
        });
    }

    private static Optional<AnnotationInfo> relationAnnotation(FieldInfo field) {
        for (String name : List.of("OneToMany", "ManyToOne", "OneToOne", "ManyToMany")) {
            Optional<AnnotationInfo> annotation = persistence(field.annotations(), name);
            if (annotation.isPresent()) {
                return annotation;
            }
        }
        return Optional.empty();
    }

    /** The entity a field leads to: {@code targetEntity}, the type of the field, or what a collection holds. */
    private String target(FieldInfo field, TypeRef type) {
        Optional<String> explicit = relationAnnotation(field).map(annotation -> annotation.classValue("targetEntity")).filter(name -> name != null && !name.equals("void"));
        if (explicit.isPresent()) {
            return explicit.get();
        }
        for (String candidate : type.classes()) {
            if (entities.containsKey(candidate)) {
                return candidate;
            }
        }
        return type.classes().stream().filter(name -> !name.startsWith("java.")).findFirst().orElse(null);
    }

    private static boolean isCollection(TypeRef type) {
        return type.isArray() || COLLECTIONS.contains(type.name());
    }

    private String columnName(FieldInfo field) {
        if (settings.tableNames()) {
            Optional<String> column = persistence(field.annotations(), "Column").map(annotation -> annotation.string("name", "")).filter(name -> !name.isBlank());
            if (column.isPresent()) {
                return Mermaid.name(column.get());
            }
        }
        return Mermaid.name(field.name());
    }

    private String foreignKeyName(FieldInfo field) {
        if (settings.tableNames()) {
            Optional<String> join = persistence(field.annotations(), "JoinColumn").map(annotation -> annotation.string("name", "")).filter(name -> !name.isBlank());
            if (join.isPresent()) {
                return Mermaid.name(join.get());
            }
        }
        return Mermaid.name(field.name() + "_id");
    }

    /** The type of the primary key of an entity, for the foreign keys that point at it. */
    private String idType(TypeInfo entity) {
        for (FieldInfo field : fieldsOf(entity)) {
            if (field.annotations().stream().anyMatch(annotation -> settings.idAnnotations().contains(annotation.type()))) {
                return dbType(field.type());
            }
        }
        return "long";
    }

    /** A name for the type of a column that is short and that Mermaid accepts. */
    private String dbType(TypeRef type) {
        if (type.isArray()) {
            return type.name().equals("byte") ? "blob" : "list";
        }
        return switch (type.name()) {
            case "java.lang.String", "char", "java.lang.Character" -> "string";
            case "int", "short", "byte", "java.lang.Integer", "java.lang.Short", "java.lang.Byte" -> "int";
            case "long", "java.lang.Long" -> "long";
            case "boolean", "java.lang.Boolean" -> "boolean";
            case "double", "float", "java.lang.Double", "java.lang.Float" -> "double";
            case "java.math.BigDecimal" -> "decimal";
            case "java.math.BigInteger" -> "bigint";
            case "java.time.LocalDate", "java.sql.Date" -> "date";
            case "java.time.LocalTime", "java.sql.Time" -> "time";
            case "java.util.UUID" -> "uuid";
            case "java.util.Optional" -> type.arguments().isEmpty() ? "object" : dbType(type.arguments().get(0));
            default -> other(type);
        };
    }

    private String other(TypeRef type) {
        if (TIMESTAMPS.contains(type.name())) {
            return "timestamp";
        }
        if (COLLECTIONS.contains(type.name())) {
            return "list";
        }
        if (scope.type(type.name()).map(TypeInfo::isEnum).orElse(false)) {
            return "enum";
        }
        return Mermaid.name(type.variable() ? "object" : type.simpleName());
    }

    // ---- relationships

    /**
     * A relation is often declared on both sides: a customer has orders, an order has a customer. It is one relation, and it is
     * drawn once, with the name of the side that owns it.
     */
    private List<Relationship> merge(Map<String, List<Relationship>> all) {
        Map<String, Relationship> merged = new LinkedHashMap<>();
        for (List<Relationship> relationships : all.values()) {
            for (Relationship relationship : relationships) {
                Relationship normalized = normalize(relationship);
                String key = normalized.entity() + ">" + normalized.target() + ">" + normalized.kind();
                Relationship existing = merged.get(key);
                if (existing == null || existing.inverse() && !normalized.inverse()) {
                    merged.put(key, normalized);
                }
            }
        }
        return merged.values().stream().sorted(Comparator.comparing(Relationship::entity).thenComparing(Relationship::target).thenComparing(Relationship::label)).toList();
    }

    /** The side with one comes first: a many-to-one relation is read the other way round, as one-to-many. */
    private static Relationship normalize(Relationship relationship) {
        return switch (relationship.kind()) {
            case MANY_TO_ONE -> new Relationship(relationship.target(), relationship.entity(), Cardinality.ONE_TO_MANY, relationship.label(), relationship.optional(), relationship.inverse());
            case ONE_TO_ONE, MANY_TO_MANY -> relationship.entity().compareTo(relationship.target()) <= 0
                    ? relationship
                    : new Relationship(relationship.target(), relationship.entity(), relationship.kind(), relationship.label(), relationship.optional(), relationship.inverse());
            default -> relationship;
        };
    }

    private static String line(Relationship relationship, Map<String, String> names) {
        String left = names.get(relationship.entity());
        String right = names.get(relationship.target());
        String cardinality = switch (relationship.kind()) {
            case ONE_TO_MANY -> relationship.optional() ? "|o--o{" : "||--o{";
            case ONE_TO_ONE -> relationship.optional() ? "|o--o|" : "||--||";
            case MANY_TO_MANY -> "}o--o{";
            case MANY_TO_ONE -> "}o--||";
        };
        return left + " " + cardinality + " " + right + " : \"" + Mermaid.label(relationship.label()) + "\"";
    }
}
