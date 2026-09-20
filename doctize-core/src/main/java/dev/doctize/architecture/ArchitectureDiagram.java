package dev.doctize.architecture;

import dev.doctize.architecture.ArchitectureModel.Edge;
import dev.doctize.architecture.ArchitectureModel.Node;
import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.ArchitectureSettings.Direction;
import dev.doctize.text.Html;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Draws the map as an SVG: one box per {@link Node}, one arrow per dependency, with the boxes that depend on others above
 * (or left of) the ones they depend on. The colours come from CSS classes of the theme, so the map follows light and dark mode.
 *
 * <p>The layout is a layered one. A box's row is the length of the longest chain of dependents above it, or the position of
 * its layer if layers are configured; boxes in a cycle share a row. Inside a row the boxes are ordered to keep arrows from
 * crossing, by repeatedly placing each box at the average position of its neighbours.
 */
final class ArchitectureDiagram {

    private static final double PADDING = 24;
    private static final double BOX_HEIGHT = 46;
    private static final double MIN_BOX_WIDTH = 104;
    private static final double CHARACTER_WIDTH = 7.8;
    private static final double GAP_ALONG = 36;
    private static final double GAP_BETWEEN_RANKS = 84;
    private static final int ORDERING_PASSES = 6;
    private static final double MIN_ARC = 34;
    private static final double MAX_ARC = 130;

    private final ArchitectureModel model;
    private final Direction direction;
    private final List<Edge> edges;
    private final Set<String> cycleMembers = new HashSet<>();
    private final Set<String> violations = new HashSet<>();
    private final Set<String> reversed = new HashSet<>();

    private ArchitectureDiagram(ArchitectureModel model, ArchitectureSettings settings) {
        this.model = model;
        this.direction = settings.direction();
        this.edges = model.edges().stream().filter(edge -> edge.references() >= settings.minReferences()).toList();
        model.cycles().forEach(cycleMembers::addAll);
        model.violations().forEach(violation -> violations.add(violation.from() + "\u0000" + violation.to()));
        edges.forEach(edge -> reversed.add(edge.to() + "\u0000" + edge.from()));
    }

    static String svg(ArchitectureModel model, ArchitectureSettings settings) {
        return new ArchitectureDiagram(model, settings).draw();
    }

    // ---- layout

    /** A box with its place. */
    private static final class Box {
        final Node node;
        final double width;
        final double height;
        int rank;
        double x;
        double y;

        Box(Node node) {
            this.node = node;
            this.width = Math.max(MIN_BOX_WIDTH, node.name().length() * CHARACTER_WIDTH + 28);
            this.height = BOX_HEIGHT;
        }

        double centerX() {
            return x + width / 2;
        }

        double centerY() {
            return y + height / 2;
        }
    }

    private String draw() {
        Map<String, Box> boxes = new HashMap<>();
        List<Box> all = new ArrayList<>();
        for (Node node : model.nodes()) {
            Box box = new Box(node);
            boxes.put(node.name(), box);
            all.add(box);
        }
        assignRanks(all, boxes);
        List<List<Box>> rows = orderedRows(all, boxes);
        double[] size = place(rows);
        return svg(boxes, size[0], size[1]);
    }

    /** Dependents above their dependencies; boxes that depend on each other share a row. Configured layers decide the rows they name. */
    private void assignRanks(List<Box> all, Map<String, Box> boxes) {
        Map<String, List<String>> successors = new HashMap<>();
        all.forEach(box -> successors.put(box.node.name(), new ArrayList<>()));
        edges.forEach(edge -> successors.get(edge.from()).add(edge.to()));
        List<List<String>> components = new ArchitectureAnalyzer.Tarjan(successors, all.stream().map(b -> b.node.name()).toList()).components();
        // Tarjan yields a component after everything it depends on, so walking backwards visits dependents first.
        Map<String, Integer> componentOf = new HashMap<>();
        for (int i = 0; i < components.size(); i++) {
            for (String name : components.get(i)) {
                componentOf.put(name, i);
            }
        }
        int[] rank = new int[components.size()];
        for (int i = components.size() - 1; i >= 0; i--) {
            for (String name : components.get(i)) {
                for (String next : successors.get(name)) {
                    int target = componentOf.get(next);
                    if (target != i) {
                        rank[target] = Math.max(rank[target], rank[i] + 1);
                    }
                }
            }
        }
        int layers = model.nodes().stream().mapToInt(Node::layer).max().orElse(-1) + 1;
        for (Box box : all) {
            int computed = rank[componentOf.get(box.node.name())];
            box.rank = box.node.layer() >= 0 ? box.node.layer() : layers + computed;
        }
        compact(all);
    }

    /** Removes empty rows, so that a layer without boxes does not leave a gap. */
    private static void compact(List<Box> all) {
        List<Integer> used = all.stream().map(box -> box.rank).distinct().sorted().toList();
        for (Box box : all) {
            box.rank = used.indexOf(box.rank);
        }
    }

    private List<List<Box>> orderedRows(List<Box> all, Map<String, Box> boxes) {
        int rowCount = all.stream().mapToInt(box -> box.rank).max().orElse(-1) + 1;
        List<List<Box>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            int row = i;
            rows.add(new ArrayList<>(all.stream().filter(box -> box.rank == row).sorted(Comparator.comparing(box -> box.node.name())).toList()));
        }
        Map<String, List<String>> neighbours = new HashMap<>();
        all.forEach(box -> neighbours.put(box.node.name(), new ArrayList<>()));
        for (Edge edge : edges) {
            neighbours.get(edge.from()).add(edge.to());
            neighbours.get(edge.to()).add(edge.from());
        }
        for (int pass = 0; pass < ORDERING_PASSES; pass++) {
            Map<String, Double> position = new HashMap<>();
            for (List<Box> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    position.put(row.get(i).node.name(), (i + 0.5) / row.size());
                }
            }
            for (List<Box> row : rows) {
                Map<String, Double> barycentre = new HashMap<>();
                for (Box box : row) {
                    List<String> around = neighbours.get(box.node.name());
                    barycentre.put(box.node.name(), around.isEmpty()
                            ? position.get(box.node.name())
                            : around.stream().mapToDouble(position::get).average().orElseThrow());
                }
                row.sort(Comparator.<Box>comparingDouble(box -> barycentre.get(box.node.name())).thenComparing(box -> box.node.name()));
            }
        }
        return rows;
    }

    /** Sets the coordinates and returns the size of the drawing. */
    private double[] place(List<List<Box>> rows) {
        boolean down = direction == Direction.TOP_DOWN;
        double longest = 0;
        for (List<Box> row : rows) {
            longest = Math.max(longest, row.stream().mapToDouble(box -> along(box, down)).sum() + GAP_ALONG * Math.max(0, row.size() - 1));
        }
        double position = PADDING;
        for (List<Box> row : rows) {
            double thickness = row.stream().mapToDouble(box -> thickness(box, down)).max().orElse(0);
            double length = row.stream().mapToDouble(box -> along(box, down)).sum() + GAP_ALONG * Math.max(0, row.size() - 1);
            double cursor = PADDING + (longest - length) / 2;
            for (Box box : row) {
                double centred = position + (thickness - thickness(box, down)) / 2;
                box.x = down ? cursor : centred;
                box.y = down ? centred : cursor;
                cursor += along(box, down) + GAP_ALONG;
            }
            position += thickness + GAP_BETWEEN_RANKS;
        }
        double rankExtent = rows.isEmpty() ? 0 : position - GAP_BETWEEN_RANKS - PADDING;
        double alongExtent = longest;
        // Arrows within the first row arc over its outer side and need room there; the rows move to make it.
        double headroom = rows.isEmpty() ? 0 : headroomForArcs(rows.get(0), down);
        if (headroom > 0) {
            for (List<Box> row : rows) {
                for (Box box : row) {
                    if (down) {
                        box.y += headroom;
                    } else {
                        box.x += headroom;
                    }
                }
            }
        }
        return down
                ? new double[] {alongExtent + 2 * PADDING, rankExtent + 2 * PADDING + headroom}
                : new double[] {rankExtent + 2 * PADDING + headroom, alongExtent + 2 * PADDING};
    }

    /** How much room the highest arc over the first row needs beyond the padding. */
    private double headroomForArcs(List<Box> firstRow, boolean down) {
        double needed = 0;
        for (Edge edge : edges) {
            Box from = firstRow.stream().filter(box -> box.node.name().equals(edge.from())).findFirst().orElse(null);
            Box to = firstRow.stream().filter(box -> box.node.name().equals(edge.to())).findFirst().orElse(null);
            if (from != null && to != null) {
                double distance = down ? Math.abs(from.centerX() - to.centerX()) : Math.abs(from.centerY() - to.centerY());
                needed = Math.max(needed, 0.75 * arcHeight(distance));
            }
        }
        return needed > PADDING ? needed - PADDING + 6 : 0;
    }

    /** The further apart two boxes in a row are, the higher the arc between them, so arcs do not lie on top of each other. */
    private static double arcHeight(double distance) {
        return Math.min(MAX_ARC, MIN_ARC + distance * 0.22);
    }

    private static double along(Box box, boolean down) {
        return down ? box.width : box.height;
    }

    private static double thickness(Box box, boolean down) {
        return down ? box.height : box.width;
    }

    // ---- drawing

    private String svg(Map<String, Box> boxes, double width, double height) {
        StringBuilder out = new StringBuilder();
        String label = "Dependencies between " + boxes.size() + " parts of the architecture";
        out.append("<div class=\"arch-figure\">\n");
        out.append(String.format(Locale.ROOT,
                "<svg class=\"arch-map\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.0f %.0f\" width=\"%.0f\" height=\"%.0f\" role=\"img\" aria-label=\"%s\">%n",
                width, height, width, height, Html.escape(label)));
        out.append("<defs>\n");
        out.append(marker("arch-arrow", "arch-arrowhead"));
        out.append(marker("arch-arrow-alert", "arch-arrowhead arch-arrowhead--alert"));
        out.append("</defs>\n");
        out.append("<g class=\"arch-edges\">\n");
        for (Edge edge : edges) {
            out.append(edge(edge, boxes.get(edge.from()), boxes.get(edge.to())));
        }
        out.append("</g>\n<g class=\"arch-nodes\">\n");
        for (Box box : boxes.values().stream().sorted(Comparator.comparing(b -> b.node.name())).toList()) {
            out.append(node(box));
        }
        out.append("</g>\n</svg>\n</div>\n");
        return out.toString();
    }

    private static String marker(String id, String classes) {
        return "<marker id=\"" + id + "\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" markerUnits=\"userSpaceOnUse\" markerWidth=\"9\" markerHeight=\"9\" orient=\"auto-start-reverse\">"
                + "<path class=\"" + classes + "\" d=\"M0,0 L10,5 L0,10 z\"/></marker>\n";
    }

    private String node(Box box) {
        Node node = box.node;
        String classes = "arch-node" + (node.component() ? " arch-node--component" : "") + (cycleMembers.contains(node.name()) ? " arch-node--cycle" : "");
        String subtitle = (node.packages().size() > 1 ? node.packages().size() + " packages · " : "") + node.types() + (node.types() == 1 ? " type" : " types");
        String tooltip = node.name() + "\n" + String.join("\n", node.packages());
        return String.format(Locale.ROOT,
                "<g class=\"%s\" transform=\"translate(%.1f %.1f)\"><title>%s</title>"
                        + "<rect width=\"%.1f\" height=\"%.1f\" rx=\"8\" fill=\"none\" stroke=\"currentColor\"/>"
                        + "<text class=\"arch-name\" x=\"%.1f\" y=\"20\" text-anchor=\"middle\">%s</text>"
                        + "<text class=\"arch-sub\" x=\"%.1f\" y=\"36\" text-anchor=\"middle\">%s</text></g>%n",
                classes, box.x, box.y, Html.escape(tooltip), box.width, box.height, box.width / 2, Html.escape(node.name()),
                box.width / 2, Html.escape(subtitle));
    }

    private String edge(Edge edge, Box from, Box to) {
        boolean alert = violations.contains(edge.from() + "\u0000" + edge.to())
                || (cycleMembers.contains(edge.from()) && cycleMembers.contains(edge.to()) && sameCycle(edge));
        boolean down = direction == Direction.TOP_DOWN;
        // Arrows in both directions between two boxes would lie on top of each other, so they are moved apart.
        double shift = reversed.contains(edge.from() + "\u0000" + edge.to()) ? (edge.from().compareTo(edge.to()) < 0 ? -9 : 9) : 0;
        double[] start;
        double[] end;
        String path;
        if (from.rank == to.rank) {
            // Same row: an arc over the top (or, from left to right, over the left side).
            start = down ? new double[] {from.centerX() + shift, from.y} : new double[] {from.x, from.centerY() + shift};
            end = down ? new double[] {to.centerX() + shift, to.y} : new double[] {to.x, to.centerY() + shift};
            double lift = arcHeight(down ? Math.abs(start[0] - end[0]) : Math.abs(start[1] - end[1]));
            path = down
                    ? String.format(Locale.ROOT, "M%.1f,%.1f C%.1f,%.1f %.1f,%.1f %.1f,%.1f", start[0], start[1], start[0], start[1] - lift, end[0], end[1] - lift, end[0], end[1])
                    : String.format(Locale.ROOT, "M%.1f,%.1f C%.1f,%.1f %.1f,%.1f %.1f,%.1f", start[0], start[1], start[0] - lift, start[1], end[0] - lift, end[1], end[0], end[1]);
        } else {
            boolean forward = to.rank > from.rank;
            if (down) {
                start = new double[] {from.centerX() + shift, forward ? from.y + from.height : from.y};
                end = new double[] {to.centerX() + shift, forward ? to.y : to.y + to.height};
                double half = (end[1] - start[1]) / 2;
                path = String.format(Locale.ROOT, "M%.1f,%.1f C%.1f,%.1f %.1f,%.1f %.1f,%.1f", start[0], start[1], start[0], start[1] + half, end[0], end[1] - half, end[0], end[1]);
            } else {
                start = new double[] {forward ? from.x + from.width : from.x, from.centerY() + shift};
                end = new double[] {forward ? to.x : to.x + to.width, to.centerY() + shift};
                double half = (end[0] - start[0]) / 2;
                path = String.format(Locale.ROOT, "M%.1f,%.1f C%.1f,%.1f %.1f,%.1f %.1f,%.1f", start[0], start[1], start[0] + half, start[1], end[0] - half, end[1], end[0], end[1]);
            }
        }
        double strokeWidth = Math.min(4, 1 + 0.6 * (Math.log(edge.references()) / Math.log(2)));
        return String.format(Locale.ROOT,
                "<path class=\"arch-edge%s\" d=\"%s\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"%.1f\" marker-end=\"url(#%s)\"><title>%s</title></path>%n",
                alert ? " arch-edge--alert" : "", path, strokeWidth, alert ? "arch-arrow-alert" : "arch-arrow",
                Html.escape(edge.from() + " depends on " + edge.to() + " (" + edge.references() + (edge.references() == 1 ? " reference)" : " references)")));
    }

    private boolean sameCycle(Edge edge) {
        return model.cycles().stream().anyMatch(cycle -> cycle.contains(edge.from()) && cycle.contains(edge.to()));
    }
}
