package dev.doctize.architecture;

import dev.doctize.annotations.Doc;
import java.util.List;

/**
 * The architecture of a project as found by {@link ArchitectureAnalyzer}: boxes, the dependencies between them, and
 * everything that is wrong with them.
 *
 * @param rootPackage the package the map starts at; empty if the classes share none
 * @param types the number of types analyzed; nested types count with the type they are in
 * @param packages the number of packages analyzed
 * @param nodes the boxes, in a stable order
 * @param edges every dependency between two boxes, including the ones below the {@code minReferences} of the map
 * @param cycles groups of boxes that depend on each other, directly or not
 * @param violations broken layer rules and forbidden dependencies
 * @param external the libraries used, if requested
 */
@Doc(group = "Architecture", order = 70)
public record ArchitectureModel(
        String rootPackage,
        int types,
        int packages,
        List<Node> nodes,
        List<Edge> edges,
        List<List<String>> cycles,
        List<Violation> violations,
        List<ExternalLibrary> external) {

    public ArchitectureModel {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        cycles = cycles.stream().map(List::copyOf).toList();
        violations = List.copyOf(violations);
        external = List.copyOf(external);
    }

    /**
     * A box on the map: a component, or a package (with the packages merged into it).
     *
     * @param name the label
     * @param component whether it is a configured component rather than a package
     * @param description what a component is for; empty for a package
     * @param packages the packages in it
     * @param types the number of types in it
     * @param layer the position of its layer in the configured {@code layers}, or {@code -1} if it is in none
     */
    public record Node(String name, boolean component, String description, List<String> packages, int types, int layer) {

        public Node {
            packages = List.copyOf(packages);
        }
    }

    /**
     * A dependency: {@code from} refers to {@code to}.
     *
     * @param from the name of the box that depends on the other
     * @param to the name of the box it depends on
     * @param references how many pairs of types there are where a type of {@code from} refers to a type of {@code to}
     */
    public record Edge(String from, String to, int references) {}

    /**
     * A broken rule.
     *
     * @param rule {@code layer} or {@code forbidden}
     * @param from the box that depends on {@code to}
     * @param to the box it must not depend on
     * @param references how many pairs of types are involved
     * @param message what is wrong, as a sentence
     */
    public record Violation(String rule, String from, String to, int references, String message) {}

    /**
     * A library the project uses.
     *
     * @param name the package prefix that identifies it, e.g. {@code com.fasterxml.jackson}
     * @param usedBy the boxes that use it
     * @param references how many pairs of types are involved
     */
    public record ExternalLibrary(String name, List<String> usedBy, int references) {

        public ExternalLibrary {
            usedBy = List.copyOf(usedBy);
        }
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }
}
