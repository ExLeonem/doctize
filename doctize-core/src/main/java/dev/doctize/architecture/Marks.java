package dev.doctize.architecture;

import dev.doctize.architecture.TypeInfo.AnnotationInfo;
import dev.doctize.config.DiagramSettings.Kind;
import java.util.List;

/**
 * The annotations that the architecture generator reads from class files, and what a mark on a code part means for a kind of
 * diagram.
 */
final class Marks {

    static final String PACKAGE = "dev.doctize.annotations.";
    static final String IGNORE = PACKAGE + "DiagramIgnore";
    static final String INCLUDE = PACKAGE + "DiagramInclude";
    static final String STATE_MACHINE = PACKAGE + "StateMachine";
    static final String TRANSITION = PACKAGE + "Transition";
    static final String TRANSITIONS = PACKAGE + "Transitions";
    static final String FLOW = PACKAGE + "Flow";

    /** What the author said about a code part: nothing, that it has to be shown, or that it must not be. */
    enum Mark {
        NONE,
        INCLUDE,
        IGNORE;

        boolean ignored() {
            return this == IGNORE;
        }

        boolean included() {
            return this == INCLUDE;
        }
    }

    private Marks() {}

    /** The mark of a code part for a kind of diagram. If it is both included and ignored, it is ignored. */
    static Mark of(List<AnnotationInfo> annotations, Kind kind) {
        boolean include = false;
        for (AnnotationInfo annotation : annotations) {
            if (annotation.type().equals(IGNORE) && appliesTo(annotation, kind)) {
                return Mark.IGNORE;
            }
            if (annotation.type().equals(INCLUDE) && appliesTo(annotation, kind)) {
                include = true;
            }
        }
        return include ? Mark.INCLUDE : Mark.NONE;
    }

    /** A mark without {@code types} applies to every kind of diagram, otherwise only to the kinds it names. */
    private static boolean appliesTo(AnnotationInfo annotation, Kind kind) {
        List<String> types = annotation.strings("types");
        return types.isEmpty() || types.stream().anyMatch(name -> name.strip().equalsIgnoreCase(kind.id()));
    }
}
