package dev.doctize.architecture;

import dev.doctize.annotations.Doc;
import dev.doctize.architecture.ArchitectureModel.Edge;
import dev.doctize.architecture.ArchitectureModel.ExternalLibrary;
import dev.doctize.architecture.ArchitectureModel.Node;
import dev.doctize.architecture.ArchitectureModel.Violation;
import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.DiagramSettings.Kind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Finds the architecture of compiled code: which packages there are, how they depend on each other, whether they form
 * cycles, and whether they follow the rules in the {@link ArchitectureSettings}.
 */
@Doc(group = "Architecture", order = 71)
public final class ArchitectureAnalyzer {

    private static final List<String> JDK_PREFIXES = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.");

    private final ArchitectureSettings settings;

    public ArchitectureAnalyzer(ArchitectureSettings settings) {
        this.settings = settings;
    }

    /** Analyzes the classes in the given directories and jars. */
    public ArchitectureModel analyze(List<Path> classRoots) {
        return analyzeClasses(ClassScanner.scan(classRoots));
    }

    /** Analyzes classes that were scanned already, with what {@code @DiagramIgnore} and {@code @DiagramInclude} say about them. */
    ArchitectureModel analyze(ClassScanner.Scan scan) {
        DiagramScope scope = new DiagramScope(settings, scan);
        Set<String> ignored = new HashSet<>();
        Set<String> included = new HashSet<>();
        for (TypeInfo type : scope.types()) {
            switch (scope.effectiveMark(type, Kind.OVERVIEW)) {
                case IGNORE -> ignored.add(type.name());
                case INCLUDE -> included.add(type.name());
                case NONE -> { }
            }
        }
        return analyzeClasses(scan.types().stream().map(type -> new ScannedClass(type.name(), type.packageName(), type.references())).toList(), ignored, included);
    }

    private ArchitectureModel analyzeClasses(List<ScannedClass> found) {
        return analyzeClasses(found, Set.of(), Set.of());
    }

    /**
     * @param ignored the types that must not be part of the analysis; the types nested in them are not either
     * @param included the types that are part of it although the configuration would leave them out
     */
    private ArchitectureModel analyzeClasses(List<ScannedClass> found, Set<String> ignored, Set<String> included) {
        Set<String> knownTypes = new HashSet<>();
        found.forEach(scanned -> knownTypes.add(scanned.name()));
        // An ignored type is still part of the project, so a dependency on it is not a dependency on a library.
        List<ScannedClass> kept = found.stream().filter(scanned -> !isIgnored(scanned.name(), ignored)).toList();
        List<ScannedClass> all = ClassScanner.mergeNestedClasses(kept);
        all.forEach(scanned -> knownTypes.add(scanned.name()));
        List<ScannedClass> shown = all.stream().filter(scanned -> included.contains(scanned.name()) || isShown(scanned.packageName())).toList();
        String root = settings.rootPackage().isEmpty() ? commonPrefix(shown) : settings.rootPackage();
        List<ScannedClass> inRoot = shown.stream().filter(scanned -> included.contains(scanned.name()) || isBelow(scanned.packageName(), root)).toList();
        Set<String> shownTypes = new HashSet<>();
        inRoot.forEach(scanned -> shownTypes.add(scanned.name()));

        Map<String, String> nodeOfPackage = new HashMap<>();
        Map<String, ArchitectureSettings.Component> declared = new HashMap<>();
        settings.components().forEach(component -> declared.put(component.name(), component));
        Map<String, NodeBuilder> nodes = new TreeMap<>();
        for (ScannedClass scanned : inRoot) {
            String node = nodeOfPackage.computeIfAbsent(scanned.packageName(), pkg -> nodeName(pkg, root));
            NodeBuilder builder = nodes.computeIfAbsent(node, name -> new NodeBuilder(name, declared.get(name)));
            builder.packages.add(scanned.packageName());
            builder.types++;
        }

        Map<String, Integer> pairs = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> externalByLibrary = new TreeMap<>();
        Map<String, String> packageOfType = new HashMap<>();
        inRoot.forEach(scanned -> packageOfType.put(scanned.name(), scanned.packageName()));
        List<ExampleReference> examples = new ArrayList<>();
        for (ScannedClass from : inRoot) {
            String fromNode = nodeOfPackage.get(from.packageName());
            for (String target : from.references()) {
                if (shownTypes.contains(target)) {
                    String toNode = nodeOfPackage.get(packageOfType.get(target));
                    if (!toNode.equals(fromNode)) {
                        pairs.merge(fromNode + "\u0000" + toNode, 1, Integer::sum);
                    }
                    if (!packageOfType.get(target).equals(from.packageName())) {
                        examples.add(new ExampleReference(from.name(), target));
                    }
                } else if (!knownTypes.contains(target) && isLibrary(target)) {
                    externalByLibrary.computeIfAbsent(library(target), key -> new TreeMap<>()).merge(fromNode, 1, Integer::sum);
                }
            }
        }

        List<Node> nodeList = nodes.values().stream().map(this::toNode).toList();
        List<Edge> edges = new ArrayList<>();
        pairs.forEach((key, references) -> {
            String[] ends = key.split("\u0000");
            edges.add(new Edge(ends[0], ends[1], references));
        });
        edges.sort(Comparator.comparing(Edge::from).thenComparing(Edge::to));

        List<List<String>> cycles = settings.cycles() ? cyclesOf(nodeList, edges) : List.of();
        List<Violation> violations = new ArrayList<>(layerViolations(nodeList, edges));
        violations.addAll(forbiddenDependencies(examples, packageOfType, nodeOfPackage));
        return new ArchitectureModel(
                root,
                inRoot.size(),
                nodeOfPackage.size(),
                nodeList,
                edges,
                cycles,
                violations,
                settings.external() ? externals(externalByLibrary) : List.of());
    }

    // ---- what is shown, and how it is grouped

    /** A type is ignored if it is marked so, or is nested in one that is: {@code com.acme.Order$Line} is ignored with {@code com.acme.Order}. */
    private static boolean isIgnored(String name, Set<String> ignored) {
        if (ignored.contains(name)) {
            return true;
        }
        for (int dollar = name.indexOf('$', 1); dollar > 0; dollar = name.indexOf('$', dollar + 1)) {
            if (ignored.contains(name.substring(0, dollar))) {
                return true;
            }
        }
        return false;
    }

    private boolean isShown(String packageName) {
        boolean included = settings.include().isEmpty() || settings.include().stream().anyMatch(p -> PackagePattern.matches(p, packageName));
        return included && settings.exclude().stream().noneMatch(p -> PackagePattern.matches(p, packageName));
    }

    private static boolean isBelow(String packageName, String root) {
        return root.isEmpty() || packageName.equals(root) || packageName.startsWith(root + ".");
    }

    /** The longest package name that every package starts with, e.g. {@code com.acme} for {@code com.acme.api} and {@code com.acme.core}. */
    private static String commonPrefix(List<ScannedClass> classes) {
        List<String[]> packages = classes.stream().map(ScannedClass::packageName).distinct()
                .map(name -> name.isEmpty() ? new String[0] : name.split("\\.")).toList();
        if (packages.isEmpty()) {
            return "";
        }
        String[] prefix = packages.get(0);
        int length = prefix.length;
        for (String[] other : packages) {
            int common = 0;
            while (common < length && common < other.length && prefix[common].equals(other[common])) {
                common++;
            }
            length = common;
        }
        return String.join(".", java.util.Arrays.copyOf(prefix, length));
    }

    /** A component if one claims the package, otherwise the package relative to the root, cut off at {@code depth}. */
    private String nodeName(String packageName, String root) {
        for (ArchitectureSettings.Component component : settings.components()) {
            if (component.packages().stream().anyMatch(pattern -> PackagePattern.matches(pattern, packageName))) {
                return component.name();
            }
        }
        // A type that is included by a mark can be outside the root package; it is then named by its whole package.
        if (!isBelow(packageName, root)) {
            return packageName;
        }
        String relative = packageName.equals(root) ? "" : packageName.substring(root.isEmpty() ? 0 : root.length() + 1);
        if (relative.isEmpty()) {
            return root.isEmpty() ? "(default package)" : root.substring(root.lastIndexOf('.') + 1);
        }
        String[] segments = relative.split("\\.");
        int kept = settings.depth() == 0 ? segments.length : Math.min(settings.depth(), segments.length);
        return String.join(".", java.util.Arrays.copyOf(segments, kept));
    }

    private Node toNode(NodeBuilder builder) {
        Node unplaced = builder.toNode();
        for (int layer = 0; layer < settings.layers().size(); layer++) {
            if (names(settings.layers().get(layer), unplaced)) {
                return new Node(unplaced.name(), unplaced.component(), unplaced.description(), unplaced.packages(), unplaced.types(), layer);
            }
        }
        return unplaced;
    }

    /** Whether a pattern from the settings names a box: by its name, or as a pattern for one of its packages. */
    private static boolean names(String pattern, Node node) {
        return pattern.equals(node.name()) || node.packages().stream().anyMatch(pkg -> PackagePattern.matches(pattern, pkg));
    }

    // ---- cycles

    /** The groups of boxes that can reach each other (strongly connected components with more than one box). */
    private static List<List<String>> cyclesOf(List<Node> nodes, List<Edge> edges) {
        Map<String, List<String>> successors = new HashMap<>();
        nodes.forEach(node -> successors.put(node.name(), new ArrayList<>()));
        edges.forEach(edge -> successors.get(edge.from()).add(edge.to()));
        return new Tarjan(successors, nodes.stream().map(Node::name).toList()).components().stream()
                .filter(component -> component.size() > 1)
                .map(component -> component.stream().sorted().toList())
                .sorted(Comparator.comparing(component -> component.get(0)))
                .toList();
    }

    /** Tarjan's algorithm. Components are found in reverse topological order: a component before the ones that depend on it. */
    static final class Tarjan {
        private final Map<String, List<String>> successors;
        private final List<String> order;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> low = new HashMap<>();
        private final java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<List<String>> components = new ArrayList<>();

        Tarjan(Map<String, List<String>> successors, List<String> order) {
            this.successors = successors;
            this.order = order;
        }

        List<List<String>> components() {
            for (String node : order) {
                if (!index.containsKey(node)) {
                    visit(node);
                }
            }
            return components;
        }

        private void visit(String node) {
            index.put(node, index.size());
            low.put(node, index.get(node));
            stack.push(node);
            onStack.add(node);
            for (String next : successors.getOrDefault(node, List.of())) {
                if (!index.containsKey(next)) {
                    visit(next);
                    low.put(node, Math.min(low.get(node), low.get(next)));
                } else if (onStack.contains(next)) {
                    low.put(node, Math.min(low.get(node), index.get(next)));
                }
            }
            if (low.get(node).equals(index.get(node))) {
                List<String> component = new ArrayList<>();
                String member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    component.add(member);
                } while (!member.equals(node));
                components.add(component);
            }
        }
    }

    // ---- rules

    /** A box may only depend on boxes in its own layer or below it. */
    private List<Violation> layerViolations(List<Node> nodes, List<Edge> edges) {
        if (settings.layers().isEmpty()) {
            return List.of();
        }
        Map<String, Integer> layerOf = new HashMap<>();
        nodes.stream().filter(node -> node.layer() >= 0).forEach(node -> layerOf.put(node.name(), node.layer()));
        List<Violation> violations = new ArrayList<>();
        for (Edge edge : edges) {
            Integer from = layerOf.get(edge.from());
            Integer to = layerOf.get(edge.to());
            if (from != null && to != null && to < from) {
                violations.add(new Violation("layer", edge.from(), edge.to(), edge.references(),
                        edge.from() + " (layer " + settings.layers().get(from) + ") depends on " + edge.to()
                                + " (layer " + settings.layers().get(to) + "), which is above it"));
            }
        }
        return violations;
    }

    private List<Violation> forbiddenDependencies(
            List<ExampleReference> references, Map<String, String> packageOfType, Map<String, String> nodeOfPackage) {
        List<Violation> violations = new ArrayList<>();
        for (ArchitectureSettings.ForbiddenDependency rule : settings.forbid()) {
            Map<String, Integer> counts = new TreeMap<>();
            Map<String, String> example = new HashMap<>();
            for (ExampleReference reference : references) {
                String fromPackage = packageOfType.get(reference.from());
                String toPackage = packageOfType.get(reference.to());
                if (matches(rule.from(), fromPackage, nodeOfPackage) && matches(rule.to(), toPackage, nodeOfPackage)) {
                    String key = fromPackage + "\u0000" + toPackage;
                    counts.merge(key, 1, Integer::sum);
                    example.putIfAbsent(key, simpleName(reference.from()) + " → " + simpleName(reference.to()));
                }
            }
            counts.forEach((key, count) -> {
                String[] ends = key.split("\u0000");
                violations.add(new Violation("forbidden", nodeOfPackage.get(ends[0]), nodeOfPackage.get(ends[1]), count,
                        ends[0] + " must not depend on " + ends[1] + " (e.g. " + example.get(key) + ")"
                                + (rule.reason().isBlank() ? "" : ": " + rule.reason())));
            });
        }
        return violations;
    }

    private static boolean matches(String pattern, String packageName, Map<String, String> nodeOfPackage) {
        return pattern.equals(nodeOfPackage.get(packageName)) || PackagePattern.matches(pattern, packageName);
    }

    private static String simpleName(String binaryName) {
        return binaryName.substring(binaryName.lastIndexOf('.') + 1);
    }

    // ---- libraries

    /** The annotations of Doctize are kept in the class file, but they are markers for these diagrams and no library of the project. */
    private static boolean isLibrary(String binaryName) {
        return JDK_PREFIXES.stream().noneMatch(binaryName::startsWith) && binaryName.indexOf('.') > 0 && !binaryName.startsWith(Marks.PACKAGE);
    }

    private String library(String binaryName) {
        String[] segments = binaryName.split("\\.");
        // The last segment is a class, not a package.
        return String.join(".", java.util.Arrays.copyOf(segments, Math.min(settings.libraryDepth(), segments.length - 1)));
    }

    private static List<ExternalLibrary> externals(Map<String, Map<String, Integer>> byLibrary) {
        List<ExternalLibrary> libraries = new ArrayList<>();
        byLibrary.forEach((name, users) -> libraries.add(new ExternalLibrary(
                name, new ArrayList<>(new TreeSet<>(users.keySet())), users.values().stream().mapToInt(Integer::intValue).sum())));
        return libraries;
    }

    // ---- helpers

    private record ExampleReference(String from, String to) {}

    /** A box while it is being collected. */
    private static final class NodeBuilder {
        private final String name;
        private final ArchitectureSettings.Component component;
        private final Set<String> packages = new TreeSet<>();
        private int types;

        NodeBuilder(String name, ArchitectureSettings.Component component) {
            this.name = name;
            this.component = component;
        }

        Node toNode() {
            return new Node(name, component != null, component == null ? "" : component.description(), List.copyOf(packages), types, -1);
        }
    }
}
