package dev.doctize.architecture;

import java.util.List;

/**
 * One diagram of a page, with what is said about it.
 *
 * @param heading the heading above it, e.g. the name of the package
 * @param description a sentence or two above the diagram, may be empty
 * @param source the Mermaid source
 * @param items what the diagram contains, as lines of a list that follows it, so that it can be read and searched as text
 * @param notes remarks about the diagram, e.g. that it was cut off
 */
record Diagram(String heading, String description, String source, List<String> items, List<String> notes) {

    Diagram {
        description = description == null ? "" : description;
        items = List.copyOf(items);
        notes = List.copyOf(notes);
    }

    static Diagram of(String heading, String source) {
        return new Diagram(heading, "", source, List.of(), List.of());
    }
}
