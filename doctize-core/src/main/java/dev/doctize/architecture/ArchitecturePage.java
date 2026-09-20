package dev.doctize.architecture;

import dev.doctize.architecture.ArchitectureModel.Edge;
import dev.doctize.architecture.ArchitectureModel.Node;
import dev.doctize.config.ArchitectureSettings;
import java.util.List;
import java.util.stream.Collectors;

/** Writes the Markdown of the architecture page: the map first, then the same facts as tables and lists for reading and searching. */
final class ArchitecturePage {

    private final ArchitectureModel model;
    private final ArchitectureSettings settings;

    ArchitecturePage(ArchitectureModel model, ArchitectureSettings settings) {
        this.model = model;
        this.settings = settings;
    }

    String markdown() {
        StringBuilder out = new StringBuilder();
        out.append(introduction()).append("\n\n");
        out.append("## Map\n\n");
        out.append(ArchitectureDiagram.svg(model, settings)).append('\n');
        out.append("Arrows point from a part to the parts it depends on. A thicker arrow means more references,"
                + " and red marks a dependency cycle or a broken rule. Hover over a part or an arrow for details.");
        if (settings.minReferences() > 1) {
            out.append(" Dependencies with fewer than ").append(settings.minReferences()).append(" references are not drawn.");
        }
        out.append("\n\n");
        out.append(parts());
        if (!settings.layers().isEmpty()) {
            out.append(layers());
        }
        if (settings.cycles()) {
            out.append(cycles());
        }
        if (!settings.layers().isEmpty() || !settings.forbid().isEmpty()) {
            out.append(violations());
        }
        if (settings.external()) {
            out.append(libraries());
        }
        return out.toString();
    }

    private String introduction() {
        String root = model.rootPackage().isEmpty() ? "" : " below `" + model.rootPackage() + "`";
        return "The code" + root + " has **" + count(model.types(), "type") + "** in **" + count(model.packages(), "package")
                + "**, shown as **" + count(model.nodes().size(), "part") + "** with **"
                + model.edges().size() + (model.edges().size() == 1 ? " dependency" : " dependencies") + "** between them.";
    }

    private String parts() {
        boolean described = model.nodes().stream().anyMatch(node -> !node.description().isBlank());
        StringBuilder out = new StringBuilder("## Parts\n\n");
        out.append(described ? "| Part | Description | Packages | Types | Depends on | Used by |\n|---|---|---|---|---|---|\n"
                : "| Part | Packages | Types | Depends on | Used by |\n|---|---|---|---|---|\n");
        for (Node node : model.nodes()) {
            out.append("| ").append(code(node.name())).append(" | ");
            if (described) {
                out.append(cell(node.description())).append(" | ");
            }
            out.append(node.packages().stream().map(ArchitecturePage::code).collect(Collectors.joining(", "))).append(" | ")
                    .append(node.types()).append(" | ")
                    .append(names(model.edges().stream().filter(edge -> edge.from().equals(node.name())).map(Edge::to).toList())).append(" | ")
                    .append(names(model.edges().stream().filter(edge -> edge.to().equals(node.name())).map(Edge::from).toList())).append(" |\n");
        }
        return out.append('\n').toString();
    }

    private String layers() {
        StringBuilder out = new StringBuilder("## Layers\n\nFrom the top of the architecture to the bottom. A part may depend on parts in its own layer and below.\n\n");
        for (int layer = 0; layer < settings.layers().size(); layer++) {
            int index = layer;
            List<String> members = model.nodes().stream().filter(node -> node.layer() == index).map(Node::name).toList();
            out.append(layer + 1).append(". ").append(code(settings.layers().get(layer)))
                    .append(members.isEmpty() ? " (no code found)" : ": " + names(members)).append('\n');
        }
        return out.append('\n').toString();
    }

    private String cycles() {
        StringBuilder out = new StringBuilder("## Dependency cycles\n\n");
        if (model.cycles().isEmpty()) {
            return out.append("No part depends on itself through others.\n\n").toString();
        }
        out.append("Parts in a cycle cannot be understood, changed or released one without the others.\n\n");
        model.cycles().forEach(cycle -> out.append("- ").append(names(cycle)).append('\n'));
        return out.append('\n').toString();
    }

    private String violations() {
        StringBuilder out = new StringBuilder("## Rule violations\n\n");
        if (model.violations().isEmpty()) {
            return out.append("No dependency breaks a layer or a forbidden dependency.\n\n").toString();
        }
        model.violations().forEach(violation -> out.append("- ").append(escape(violation.message())).append(" — ")
                .append(count(violation.references(), "reference")).append('\n'));
        return out.append('\n').toString();
    }

    private String libraries() {
        StringBuilder out = new StringBuilder("## Libraries\n\n");
        if (model.external().isEmpty()) {
            return out.append("The code uses no library besides the JDK.\n\n").toString();
        }
        out.append("| Library | Used by | References |\n|---|---|---|\n");
        model.external().forEach(library -> out.append("| ").append(code(library.name())).append(" | ").append(names(library.usedBy()))
                .append(" | ").append(library.references()).append(" |\n"));
        return out.append('\n').toString();
    }

    private static String names(List<String> names) {
        return names.isEmpty() ? "—" : names.stream().map(ArchitecturePage::code).collect(Collectors.joining(", "));
    }

    private static String code(String text) {
        return "`" + text.replace("`", "'") + "`";
    }

    private static String cell(String text) {
        return escape(text).replace("|", "\\|").replace("\n", " ");
    }

    /** Names of packages and types contain nothing that Markdown would read, but messages can quote a reason from the configuration. */
    private static String escape(String text) {
        return text.replace("{@", "{ @");
    }

    private static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }
}
