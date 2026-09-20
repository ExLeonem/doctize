package dev.doctize.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.doctize.DoctizeException;
import dev.doctize.annotations.Doc;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How the architecture map is generated. The map is a licensed feature, see {@link dev.doctize.license.Feature#ARCHITECTURE}.
 *
 * <p>Packages are given as patterns: {@code com.acme.api} is that package, {@code com.acme.*} its direct sub-packages,
 * and {@code **} any number of packages, so {@code com.acme.api.**} is the package and everything below it, and
 * {@code **.internal} every package called {@code internal}.
 *
 * @param enabled whether to generate the map at all; off by default
 * @param title the title of the page
 * @param section the section the page is in
 * @param order where the page is in its section
 * @param rootPackage the package the map starts at: only it and the packages below it are shown, and names are given
 *     relative to it. Empty to use the longest package name all packages share.
 * @param depth how many package levels below the root are shown separately; deeper packages are merged into their
 *     ancestor. {@code 0} shows every package on its own
 * @param include only packages that match one of these patterns are shown; empty for all
 * @param exclude packages that match one of these patterns are left out, e.g. {@code **.generated}
 * @param components named groups of packages, shown as one box each
 * @param layers box names or package patterns from the top of the architecture to the bottom. A box may only depend on
 *     boxes in its own layer or below; anything else is reported
 * @param forbid dependencies that must not exist; each one that does is reported
 * @param cycles whether to look for dependency cycles, mark them on the map and report them
 * @param external whether to list the libraries the project depends on
 * @param libraryDepth how many package levels identify a library: with {@code 2}, {@code org.commonmark.node.Node} is
 *     a use of {@code org.commonmark}; with {@code 3}, {@code com.fasterxml.jackson} is told apart from other
 *     {@code com.fasterxml} libraries
 * @param minReferences the least number of references a dependency needs to be drawn on the map; the tables list all
 * @param direction which way the dependencies point on the map
 * @param diagrams which kinds of diagram are drawn, and how: the overview, class diagrams, state diagrams, entity-relationship
 *     diagrams and flow charts
 */
@Doc(group = "Configuration", order = 27)
public record ArchitectureSettings(
        boolean enabled,
        String title,
        String section,
        int order,
        String rootPackage,
        int depth,
        List<String> include,
        List<String> exclude,
        List<Component> components,
        List<String> layers,
        List<ForbiddenDependency> forbid,
        boolean cycles,
        boolean external,
        int libraryDepth,
        int minReferences,
        Direction direction,
        DiagramSettings diagrams) {

    public static final String DEFAULT_TITLE = "Architecture";
    public static final int DEFAULT_ORDER = 100;
    public static final int DEFAULT_LIBRARY_DEPTH = 2;

    public ArchitectureSettings {
        title = blank(title) ? DEFAULT_TITLE : title;
        section = blank(section) ? DEFAULT_TITLE : section;
        rootPackage = rootPackage == null ? "" : rootPackage.strip();
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
        components = components == null ? List.of() : List.copyOf(components);
        layers = layers == null ? List.of() : List.copyOf(layers);
        forbid = forbid == null ? List.of() : List.copyOf(forbid);
        direction = direction == null ? Direction.TOP_DOWN : direction;
        diagrams = diagrams == null ? DiagramSettings.defaults() : diagrams;
        if (depth < 0) {
            throw new DoctizeException("architecture.depth must not be negative, but was " + depth);
        }
        if (libraryDepth < 1) {
            throw new DoctizeException("architecture.libraryDepth must be at least 1, but was " + libraryDepth);
        }
        if (minReferences < 1) {
            throw new DoctizeException("architecture.minReferences must be at least 1, but was " + minReferences);
        }
        Set<String> names = new HashSet<>();
        for (Component component : components) {
            if (!names.add(component.name())) {
                throw new DoctizeException("architecture.components names " + component.name() + " twice");
            }
        }
    }

    /** The settings before the diagrams could be chosen: the overview only. */
    public ArchitectureSettings(
            boolean enabled,
            String title,
            String section,
            int order,
            String rootPackage,
            int depth,
            List<String> include,
            List<String> exclude,
            List<Component> components,
            List<String> layers,
            List<ForbiddenDependency> forbid,
            boolean cycles,
            boolean external,
            int libraryDepth,
            int minReferences,
            Direction direction) {
        this(enabled, title, section, order, rootPackage, depth, include, exclude, components, layers, forbid, cycles, external, libraryDepth,
                minReferences, direction, null);
    }

    /** Reads the settings from YAML, where every setting is optional. */
    @JsonCreator
    public static ArchitectureSettings fromConfig(
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("title") String title,
            @JsonProperty("section") String section,
            @JsonProperty("order") Integer order,
            @JsonProperty("rootPackage") String rootPackage,
            @JsonProperty("depth") Integer depth,
            @JsonProperty("include") List<String> include,
            @JsonProperty("exclude") List<String> exclude,
            @JsonProperty("components") List<Component> components,
            @JsonProperty("layers") List<String> layers,
            @JsonProperty("forbid") List<ForbiddenDependency> forbid,
            @JsonProperty("cycles") Boolean cycles,
            @JsonProperty("external") Boolean external,
            @JsonProperty("libraryDepth") Integer libraryDepth,
            @JsonProperty("minReferences") Integer minReferences,
            @JsonProperty("direction") Direction direction,
            @JsonProperty("diagrams") DiagramSettings diagrams) {
        return new ArchitectureSettings(
                enabled != null && enabled,
                title,
                section,
                order == null ? DEFAULT_ORDER : order,
                rootPackage,
                depth == null ? 0 : depth,
                include,
                exclude,
                components,
                layers,
                forbid,
                cycles == null || cycles,
                external != null && external,
                libraryDepth == null ? DEFAULT_LIBRARY_DEPTH : libraryDepth,
                minReferences == null ? 1 : minReferences,
                direction,
                diagrams);
    }

    /** As {@code fromConfig} with the diagrams, for settings that do not choose any: the overview only. */
    public static ArchitectureSettings fromConfig(
            Boolean enabled,
            String title,
            String section,
            Integer order,
            String rootPackage,
            Integer depth,
            List<String> include,
            List<String> exclude,
            List<Component> components,
            List<String> layers,
            List<ForbiddenDependency> forbid,
            Boolean cycles,
            Boolean external,
            Integer libraryDepth,
            Integer minReferences,
            Direction direction) {
        return fromConfig(enabled, title, section, order, rootPackage, depth, include, exclude, components, layers, forbid, cycles, external,
                libraryDepth, minReferences, direction, null);
    }

    /** The settings when there are none: the map is off. */
    public static ArchitectureSettings defaults() {
        return fromConfig(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** The same settings, with the map switched on. */
    public ArchitectureSettings turnedOn() {
        return new ArchitectureSettings(
                true, title, section, order, rootPackage, depth, include, exclude, components, layers, forbid,
                cycles, external, libraryDepth, minReferences, direction, diagrams);
    }

    /** The same settings, with the switched-on architecture and this kind of diagram on. */
    public ArchitectureSettings withDiagram(DiagramSettings.Kind kind) {
        return new ArchitectureSettings(
                true, title, section, order, rootPackage, depth, include, exclude, components, layers, forbid, cycles, external, libraryDepth,
                minReferences, direction, diagrams.with(kind));
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * A named group of packages, shown as one box on the map.
     *
     * @param name the name of the box
     * @param packages the packages in it, as patterns; a package belongs to the first component that matches it
     * @param description what the component is for, shown in the table of components; may be empty
     */
    @Doc(group = "Configuration", order = 28)
    public record Component(String name, List<String> packages, String description) {

        public Component {
            if (name == null || name.isBlank()) {
                throw new DoctizeException("A component of architecture.components needs a name");
            }
            if (packages == null || packages.isEmpty()) {
                throw new DoctizeException("The component " + name + " needs at least one package pattern in 'packages'");
            }
            packages = List.copyOf(packages);
            description = description == null ? "" : description;
        }
    }

    /**
     * A dependency that must not exist.
     *
     * @param from the packages or boxes that must not depend on {@code to}
     * @param to the packages or boxes that {@code from} must not depend on
     * @param reason why, shown with the violation; may be empty
     */
    @Doc(group = "Configuration", order = 29)
    public record ForbiddenDependency(String from, String to, String reason) {

        public ForbiddenDependency {
            if (from == null || from.isBlank() || to == null || to.isBlank()) {
                throw new DoctizeException("An entry of architecture.forbid needs 'from' and 'to'");
            }
            reason = reason == null ? "" : reason;
        }
    }

    /** Which way the dependencies point on the map. */
    @Doc(group = "Configuration", order = 29)
    public enum Direction {
        /** Boxes that depend on others are above them. */
        TOP_DOWN,
        /** Boxes that depend on others are left of them. */
        LEFT_RIGHT;

        /** Reads {@code top-down} and {@code left-right}, in any case. */
        @JsonCreator
        public static Direction of(String name) {
            String normalized = name.strip().toLowerCase(Locale.ROOT).replace('-', '_');
            for (Direction direction : values()) {
                if (direction.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return direction;
                }
            }
            throw new DoctizeException("architecture.direction must be top-down or left-right, but was " + name);
        }
    }
}
