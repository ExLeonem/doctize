package dev.doctize.architecture;

import dev.doctize.architecture.ArchitectureModel.Edge;
import dev.doctize.architecture.ArchitectureModel.Node;
import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.ArchitectureSettings.Direction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Draws the overview, the parts of the project and how they depend on each other, as a Mermaid flowchart. Parts that depend on
 * others are above them; the browser lays it out. Parts in a cycle, and arrows that are in a cycle or break a rule, are red.
 */
final class OverviewMermaid {

    private OverviewMermaid() {}

    static String source(ArchitectureModel model, ArchitectureSettings settings) {
        Map<String, String> ids = new HashMap<>();
        model.nodes().forEach(node -> ids.put(node.name(), "n" + ids.size()));
        Set<String> inCycle = new HashSet<>();
        model.cycles().forEach(inCycle::addAll);
        Set<String> violations = new HashSet<>();
        model.violations().forEach(violation -> violations.add(violation.from() + ">" + violation.to()));

        StringBuilder out = new StringBuilder("flowchart ").append(settings.direction() == Direction.LEFT_RIGHT ? "LR" : "TB").append('\n');
        for (Node node : model.nodes()) {
            String types = node.types() + (node.types() == 1 ? " type" : " types");
            String detail = node.packages().size() > 1 ? node.packages().size() + " packages · " + types : types;
            out.append("  ").append(ids.get(node.name())).append("[\"<b>").append(Mermaid.label(node.name())).append("</b><br/>").append(detail).append("\"]\n");
        }
        List<Edge> edges = model.edges().stream().filter(edge -> edge.references() >= settings.minReferences()).toList();
        List<Integer> alert = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            out.append("  ").append(ids.get(edge.from())).append(edge.references() > 1 ? " -->|" + edge.references() + "| " : " --> ").append(ids.get(edge.to())).append('\n');
            boolean sameCycle = model.cycles().stream().anyMatch(cycle -> cycle.contains(edge.from()) && cycle.contains(edge.to()));
            if (sameCycle || violations.contains(edge.from() + ">" + edge.to())) {
                alert.add(i);
            }
        }
        out.append("  classDef component stroke:#4f46e5,stroke-width:2px\n  classDef cycle stroke:#dc2626,stroke-width:2px\n");
        String components = model.nodes().stream().filter(Node::component).map(node -> ids.get(node.name())).collect(Collectors.joining(","));
        if (!components.isEmpty()) {
            out.append("  class ").append(components).append(" component\n");
        }
        String cycles = model.nodes().stream().filter(node -> inCycle.contains(node.name())).map(node -> ids.get(node.name())).collect(Collectors.joining(","));
        if (!cycles.isEmpty()) {
            out.append("  class ").append(cycles).append(" cycle\n");
        }
        if (!alert.isEmpty()) {
            out.append("  linkStyle ").append(alert.stream().map(String::valueOf).collect(Collectors.joining(","))).append(" stroke:#dc2626,stroke-width:2px\n");
        }
        return out.toString().stripTrailing();
    }
}
