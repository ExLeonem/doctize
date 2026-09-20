package dev.doctize.architecture;

import java.util.List;

/** Writes the Markdown of a page of diagrams: for each diagram a heading, what it is, the diagram, what is in it, and remarks. */
final class DiagramPage {

    private DiagramPage() {}

    static String markdown(String introduction, List<Diagram> diagrams) {
        StringBuilder out = new StringBuilder(introduction.strip()).append("\n\n");
        for (Diagram diagram : diagrams) {
            out.append("## ").append(diagram.heading()).append("\n\n");
            if (!diagram.description().isBlank()) {
                out.append(escape(diagram.description().strip())).append("\n\n");
            }
            out.append(Fences.block("mermaid", diagram.source())).append('\n');
            for (String note : diagram.notes()) {
                out.append("> [!NOTE]\n> ").append(escape(note)).append("\n\n");
            }
            diagram.items().forEach(item -> out.append("- ").append(item).append('\n'));
            if (!diagram.items().isEmpty()) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    /** Text that comes from annotations is not read as an inline tag of Javadoc. */
    private static String escape(String text) {
        return text.replace("{@", "{ @");
    }
}
