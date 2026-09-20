package dev.doctize.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.doctize.DoctizeException;
import dev.doctize.annotations.Doc;
import dev.doctize.config.ArchitectureSettings.Direction;
import java.util.List;
import java.util.Locale;

/**
 * Which diagrams the architecture generator draws, and how each one is drawn. Every kind of diagram has its own settings, and can be
 * switched on or off on its own.
 *
 * <p>In {@code doctize.yml}, a kind of diagram is written as {@code true} or {@code false}, or as a mapping of settings. A mapping
 * switches the diagram on, unless it says {@code enabled: false}. A kind that is not mentioned is on for the overview and off for the
 * others.
 *
 * @param overview the overview: the packages of the project and how they depend on each other
 * @param classes class diagrams
 * @param states state diagrams, of the enums marked with {@code @StateMachine}
 * @param entities entity-relationship diagrams of the database model
 * @param flows flow charts of the calls that start at a method marked with {@code @Flow}
 */
@Doc(group = "Configuration", order = 29)
public record DiagramSettings(
        OverviewDiagram overview, ClassDiagram classes, StateDiagram states, EntityDiagram entities, FlowDiagram flows) {

    public DiagramSettings {
        overview = overview == null ? OverviewDiagram.defaults() : overview;
        classes = classes == null ? ClassDiagram.defaults() : classes;
        states = states == null ? StateDiagram.defaults() : states;
        entities = entities == null ? EntityDiagram.defaults() : entities;
        flows = flows == null ? FlowDiagram.defaults() : flows;
    }

    /** The overview only, which is what the architecture map has always been. */
    public static DiagramSettings defaults() {
        return new DiagramSettings(null, null, null, null, null);
    }

    @JsonCreator
    public static DiagramSettings fromConfig(
            @JsonProperty("overview") OverviewDiagram overview,
            @JsonProperty("classes") ClassDiagram classes,
            @JsonProperty("states") StateDiagram states,
            @JsonProperty("entities") EntityDiagram entities,
            @JsonProperty("flows") FlowDiagram flows) {
        return new DiagramSettings(overview, classes, states, entities, flows);
    }

    /** Whether at least one kind of diagram is on. */
    public boolean anyEnabled() {
        return overview.enabled() || classes.enabled() || states.enabled() || entities.enabled() || flows.enabled();
    }

    /** The same settings with the named kind switched on. */
    public DiagramSettings with(Kind kind) {
        return switch (kind) {
            case OVERVIEW -> new DiagramSettings(overview.turnedOn(), classes, states, entities, flows);
            case CLASSES -> new DiagramSettings(overview, classes.turnedOn(), states, entities, flows);
            case STATES -> new DiagramSettings(overview, classes, states.turnedOn(), entities, flows);
            case ENTITIES -> new DiagramSettings(overview, classes, states, entities.turnedOn(), flows);
            case FLOWS -> new DiagramSettings(overview, classes, states, entities, flows.turnedOn());
        };
    }

    /** The kinds of diagram. */
    @Doc(group = "Configuration", order = 29)
    public enum Kind {
        OVERVIEW,
        CLASSES,
        STATES,
        ENTITIES,
        FLOWS;

        /** The kind with this name, in any case: {@code classes}, {@code Classes}, {@code CLASSES}. */
        public static Kind of(String name) {
            return Enums.parse(Kind.class, name, "diagram type");
        }

        /** The name in configuration files and on the command line. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How the overview is drawn. */
    @Doc(group = "Configuration", order = 29)
    public enum Format {
        /** As a Mermaid diagram, which the browser draws. */
        MERMAID,
        /** As an SVG made by Doctize, which needs no script. */
        SVG
    }

    /** The least visible members of a type that are shown. */
    @Doc(group = "Configuration", order = 29)
    public enum Visibility {
        PRIVATE,
        PACKAGE,
        PROTECTED,
        PUBLIC
    }

    /** What a class diagram is drawn for: each package, each component, or all types together. */
    @Doc(group = "Configuration", order = 29)
    public enum GroupBy {
        PACKAGE,
        COMPONENT,
        ALL
    }

    /**
     * The overview.
     *
     * @param enabled whether to draw it
     * @param format how it is drawn
     */
    @Doc(group = "Configuration", order = 29)
    public record OverviewDiagram(boolean enabled, Format format) {

        public OverviewDiagram {
            format = format == null ? Format.MERMAID : format;
        }

        public static OverviewDiagram defaults() {
            return new OverviewDiagram(true, Format.MERMAID);
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static OverviewDiagram ofFlag(boolean enabled) {
            return new OverviewDiagram(enabled, Format.MERMAID);
        }

        @JsonCreator
        public static OverviewDiagram fromConfig(@JsonProperty("enabled") Boolean enabled, @JsonProperty("format") String format) {
            return new OverviewDiagram(enabled == null || enabled, format == null ? Format.MERMAID : Enums.parse(Format.class, format, "architecture.diagrams.overview.format"));
        }

        OverviewDiagram turnedOn() {
            return new OverviewDiagram(true, format);
        }
    }

    /**
     * Class diagrams.
     *
     * @param enabled whether to draw them
     * @param title the title of the page
     * @param fields whether fields are shown
     * @param fieldVisibility the least visible fields that are shown
     * @param methods whether methods are shown
     * @param methodVisibility the least visible methods that are shown
     * @param constructors whether constructors are shown
     * @param accessors whether getters, setters, {@code equals}, {@code hashCode} and {@code toString} are shown
     * @param stereotypes whether interfaces, enums, records and abstract classes are labelled as such
     * @param inheritance whether {@code extends} and {@code implements} are drawn
     * @param associations whether fields that hold other types of the project are drawn as relations
     * @param dependencies whether parameters, return types and exceptions of methods are drawn as relations
     * @param neighbours whether types of other diagrams that the types of a diagram relate to are drawn too
     * @param groupBy what one diagram is drawn for
     * @param maxClasses the most types in one diagram; larger groups are split into several diagrams
     * @param onlyMarked whether to draw only what is marked with {@code @DiagramInclude}
     * @param includeTypes only types that match one of these patterns are drawn; empty for all
     * @param excludeTypes types that match one of these patterns are left out
     * @param direction which way the diagrams are laid out
     */
    @Doc(group = "Configuration", order = 29)
    public record ClassDiagram(
            boolean enabled,
            String title,
            boolean fields,
            Visibility fieldVisibility,
            boolean methods,
            Visibility methodVisibility,
            boolean constructors,
            boolean accessors,
            boolean stereotypes,
            boolean inheritance,
            boolean associations,
            boolean dependencies,
            boolean neighbours,
            GroupBy groupBy,
            int maxClasses,
            boolean onlyMarked,
            List<String> includeTypes,
            List<String> excludeTypes,
            Direction direction) {

        public static final String DEFAULT_TITLE = "Class diagrams";
        public static final int DEFAULT_MAX_CLASSES = 30;

        public ClassDiagram {
            title = title == null || title.isBlank() ? DEFAULT_TITLE : title;
            fieldVisibility = fieldVisibility == null ? Visibility.PRIVATE : fieldVisibility;
            methodVisibility = methodVisibility == null ? Visibility.PUBLIC : methodVisibility;
            groupBy = groupBy == null ? GroupBy.PACKAGE : groupBy;
            includeTypes = includeTypes == null ? List.of() : List.copyOf(includeTypes);
            excludeTypes = excludeTypes == null ? List.of() : List.copyOf(excludeTypes);
            direction = direction == null ? Direction.TOP_DOWN : direction;
            if (maxClasses < 1) {
                throw new DoctizeException("architecture.diagrams.classes.maxClasses must be at least 1, but was " + maxClasses);
            }
        }

        public static ClassDiagram defaults() {
            return fromConfig(false, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static ClassDiagram ofFlag(boolean enabled) {
            return fromConfig(enabled, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @JsonCreator
        public static ClassDiagram fromConfig(
                @JsonProperty("enabled") Boolean enabled,
                @JsonProperty("title") String title,
                @JsonProperty("fields") Boolean fields,
                @JsonProperty("fieldVisibility") String fieldVisibility,
                @JsonProperty("methods") Boolean methods,
                @JsonProperty("methodVisibility") String methodVisibility,
                @JsonProperty("constructors") Boolean constructors,
                @JsonProperty("accessors") Boolean accessors,
                @JsonProperty("stereotypes") Boolean stereotypes,
                @JsonProperty("inheritance") Boolean inheritance,
                @JsonProperty("associations") Boolean associations,
                @JsonProperty("dependencies") Boolean dependencies,
                @JsonProperty("neighbours") Boolean neighbours,
                @JsonProperty("groupBy") String groupBy,
                @JsonProperty("maxClasses") Integer maxClasses,
                @JsonProperty("onlyMarked") Boolean onlyMarked,
                @JsonProperty("includeTypes") List<String> includeTypes,
                @JsonProperty("excludeTypes") List<String> excludeTypes,
                @JsonProperty("direction") Direction direction) {
            return new ClassDiagram(
                    enabled == null || enabled,
                    title,
                    fields == null || fields,
                    fieldVisibility == null ? null : Enums.parse(Visibility.class, fieldVisibility, "architecture.diagrams.classes.fieldVisibility"),
                    methods == null || methods,
                    methodVisibility == null ? null : Enums.parse(Visibility.class, methodVisibility, "architecture.diagrams.classes.methodVisibility"),
                    constructors != null && constructors,
                    accessors != null && accessors,
                    stereotypes == null || stereotypes,
                    inheritance == null || inheritance,
                    associations == null || associations,
                    dependencies != null && dependencies,
                    neighbours != null && neighbours,
                    groupBy == null ? null : Enums.parse(GroupBy.class, groupBy, "architecture.diagrams.classes.groupBy"),
                    maxClasses == null ? DEFAULT_MAX_CLASSES : maxClasses,
                    onlyMarked != null && onlyMarked,
                    includeTypes,
                    excludeTypes,
                    direction);
        }

        ClassDiagram turnedOn() {
            return new ClassDiagram(true, title, fields, fieldVisibility, methods, methodVisibility, constructors, accessors, stereotypes, inheritance,
                    associations, dependencies, neighbours, groupBy, maxClasses, onlyMarked, includeTypes, excludeTypes, direction);
        }
    }

    /**
     * State diagrams.
     *
     * @param enabled whether to draw them
     * @param title the title of the page
     * @param events whether the arrows say what causes a change
     * @param direction which way the diagrams are laid out
     * @param includeTypes only enums that match one of these patterns are drawn; empty for all
     * @param excludeTypes enums that match one of these patterns are left out
     */
    @Doc(group = "Configuration", order = 29)
    public record StateDiagram(
            boolean enabled, String title, boolean events, Direction direction, List<String> includeTypes, List<String> excludeTypes) {

        public static final String DEFAULT_TITLE = "State diagrams";

        public StateDiagram {
            title = title == null || title.isBlank() ? DEFAULT_TITLE : title;
            direction = direction == null ? Direction.LEFT_RIGHT : direction;
            includeTypes = includeTypes == null ? List.of() : List.copyOf(includeTypes);
            excludeTypes = excludeTypes == null ? List.of() : List.copyOf(excludeTypes);
        }

        public static StateDiagram defaults() {
            return fromConfig(false, null, null, null, null, null);
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static StateDiagram ofFlag(boolean enabled) {
            return fromConfig(enabled, null, null, null, null, null);
        }

        @JsonCreator
        public static StateDiagram fromConfig(
                @JsonProperty("enabled") Boolean enabled,
                @JsonProperty("title") String title,
                @JsonProperty("events") Boolean events,
                @JsonProperty("direction") Direction direction,
                @JsonProperty("includeTypes") List<String> includeTypes,
                @JsonProperty("excludeTypes") List<String> excludeTypes) {
            return new StateDiagram(enabled == null || enabled, title, events == null || events, direction, includeTypes, excludeTypes);
        }

        StateDiagram turnedOn() {
            return new StateDiagram(true, title, events, direction, includeTypes, excludeTypes);
        }
    }

    /**
     * Entity-relationship diagrams.
     *
     * @param enabled whether to draw them
     * @param title the title of the page
     * @param columns whether the columns of each entity are shown
     * @param keys whether primary and foreign keys are marked
     * @param relationships whether the relations between entities are drawn
     * @param tableNames whether entities are named after their table ({@code @Table(name = ...)}) and columns after their column
     * @param onlyMarked whether to draw only what is marked with {@code @DiagramInclude}
     * @param entityAnnotations the annotations that make a class an entity, by name
     * @param idAnnotations the annotations that make a field a primary key, by name
     * @param includeTypes only entities that match one of these patterns are drawn; empty for all
     * @param excludeTypes entities that match one of these patterns are left out
     */
    @Doc(group = "Configuration", order = 29)
    public record EntityDiagram(
            boolean enabled,
            String title,
            boolean columns,
            boolean keys,
            boolean relationships,
            boolean tableNames,
            boolean onlyMarked,
            List<String> entityAnnotations,
            List<String> idAnnotations,
            List<String> includeTypes,
            List<String> excludeTypes) {

        public static final String DEFAULT_TITLE = "Data model";
        public static final List<String> DEFAULT_ENTITY_ANNOTATIONS = List.of("jakarta.persistence.Entity", "javax.persistence.Entity");
        public static final List<String> DEFAULT_ID_ANNOTATIONS =
                List.of("jakarta.persistence.Id", "jakarta.persistence.EmbeddedId", "javax.persistence.Id", "javax.persistence.EmbeddedId");

        public EntityDiagram {
            title = title == null || title.isBlank() ? DEFAULT_TITLE : title;
            entityAnnotations = entityAnnotations == null ? DEFAULT_ENTITY_ANNOTATIONS : List.copyOf(entityAnnotations);
            idAnnotations = idAnnotations == null ? DEFAULT_ID_ANNOTATIONS : List.copyOf(idAnnotations);
            includeTypes = includeTypes == null ? List.of() : List.copyOf(includeTypes);
            excludeTypes = excludeTypes == null ? List.of() : List.copyOf(excludeTypes);
        }

        public static EntityDiagram defaults() {
            return fromConfig(false, null, null, null, null, null, null, null, null, null, null);
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static EntityDiagram ofFlag(boolean enabled) {
            return fromConfig(enabled, null, null, null, null, null, null, null, null, null, null);
        }

        @JsonCreator
        public static EntityDiagram fromConfig(
                @JsonProperty("enabled") Boolean enabled,
                @JsonProperty("title") String title,
                @JsonProperty("columns") Boolean columns,
                @JsonProperty("keys") Boolean keys,
                @JsonProperty("relationships") Boolean relationships,
                @JsonProperty("tableNames") Boolean tableNames,
                @JsonProperty("onlyMarked") Boolean onlyMarked,
                @JsonProperty("entityAnnotations") List<String> entityAnnotations,
                @JsonProperty("idAnnotations") List<String> idAnnotations,
                @JsonProperty("includeTypes") List<String> includeTypes,
                @JsonProperty("excludeTypes") List<String> excludeTypes) {
            return new EntityDiagram(
                    enabled == null || enabled,
                    title,
                    columns == null || columns,
                    keys == null || keys,
                    relationships == null || relationships,
                    tableNames != null && tableNames,
                    onlyMarked != null && onlyMarked,
                    entityAnnotations,
                    idAnnotations,
                    includeTypes,
                    excludeTypes);
        }

        EntityDiagram turnedOn() {
            return new EntityDiagram(true, title, columns, keys, relationships, tableNames, onlyMarked, entityAnnotations, idAnnotations,
                    includeTypes, excludeTypes);
        }
    }

    /**
     * Flow charts.
     *
     * @param enabled whether to draw them
     * @param title the title of the page
     * @param depth how many calls deep a flow is followed
     * @param maxNodes the most methods in one flow chart; a longer flow is cut off
     * @param implementations whether a call of an interface method continues in the classes that implement it
     * @param constructors whether creating objects is shown as a step
     * @param accessors whether calls of getters and setters are shown
     * @param numbered whether the arrows are numbered in the order of the calls
     * @param direction which way the charts are laid out
     * @param entryPoints methods that start a flow in addition to those marked with {@code @Flow}, written {@code Type#method}, where
     *     the type is a pattern for a class name and the method a pattern for a method name
     * @param includeTypes only methods of types that match one of these patterns are followed; empty for all
     * @param excludeTypes methods of types that match one of these patterns are not followed
     */
    @Doc(group = "Configuration", order = 29)
    public record FlowDiagram(
            boolean enabled,
            String title,
            int depth,
            int maxNodes,
            boolean implementations,
            boolean constructors,
            boolean accessors,
            boolean numbered,
            Direction direction,
            List<String> entryPoints,
            List<String> includeTypes,
            List<String> excludeTypes) {

        public static final String DEFAULT_TITLE = "Flows";
        public static final int DEFAULT_DEPTH = 4;
        public static final int DEFAULT_MAX_NODES = 40;

        public FlowDiagram {
            title = title == null || title.isBlank() ? DEFAULT_TITLE : title;
            direction = direction == null ? Direction.TOP_DOWN : direction;
            entryPoints = entryPoints == null ? List.of() : List.copyOf(entryPoints);
            includeTypes = includeTypes == null ? List.of() : List.copyOf(includeTypes);
            excludeTypes = excludeTypes == null ? List.of() : List.copyOf(excludeTypes);
            if (depth < 1) {
                throw new DoctizeException("architecture.diagrams.flows.depth must be at least 1, but was " + depth);
            }
            if (maxNodes < 2) {
                throw new DoctizeException("architecture.diagrams.flows.maxNodes must be at least 2, but was " + maxNodes);
            }
            for (String entry : entryPoints) {
                if (entry.indexOf('#') < 0) {
                    throw new DoctizeException("An entry of architecture.diagrams.flows.entryPoints is written Type#method, but was: " + entry);
                }
            }
        }

        public static FlowDiagram defaults() {
            return fromConfig(false, null, null, null, null, null, null, null, null, null, null, null);
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static FlowDiagram ofFlag(boolean enabled) {
            return fromConfig(enabled, null, null, null, null, null, null, null, null, null, null, null);
        }

        @JsonCreator
        public static FlowDiagram fromConfig(
                @JsonProperty("enabled") Boolean enabled,
                @JsonProperty("title") String title,
                @JsonProperty("depth") Integer depth,
                @JsonProperty("maxNodes") Integer maxNodes,
                @JsonProperty("implementations") Boolean implementations,
                @JsonProperty("constructors") Boolean constructors,
                @JsonProperty("accessors") Boolean accessors,
                @JsonProperty("numbered") Boolean numbered,
                @JsonProperty("direction") Direction direction,
                @JsonProperty("entryPoints") List<String> entryPoints,
                @JsonProperty("includeTypes") List<String> includeTypes,
                @JsonProperty("excludeTypes") List<String> excludeTypes) {
            return new FlowDiagram(
                    enabled == null || enabled,
                    title,
                    depth == null ? DEFAULT_DEPTH : depth,
                    maxNodes == null ? DEFAULT_MAX_NODES : maxNodes,
                    implementations == null || implementations,
                    constructors != null && constructors,
                    accessors != null && accessors,
                    numbered == null || numbered,
                    direction,
                    entryPoints,
                    includeTypes,
                    excludeTypes);
        }

        FlowDiagram turnedOn() {
            return new FlowDiagram(true, title, depth, maxNodes, implementations, constructors, accessors, numbered, direction, entryPoints, includeTypes,
                    excludeTypes);
        }
    }
}
