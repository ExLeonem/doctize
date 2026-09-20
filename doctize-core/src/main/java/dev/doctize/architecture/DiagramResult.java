package dev.doctize.architecture;

import java.util.List;

/**
 * What a generator made: the diagrams, and the problems it found on the way, such as an annotation that names a state that does
 * not exist.
 */
record DiagramResult(List<Diagram> diagrams, List<String> warnings) {

    DiagramResult {
        diagrams = List.copyOf(diagrams);
        warnings = List.copyOf(warnings);
    }

    boolean isEmpty() {
        return diagrams.isEmpty();
    }
}
