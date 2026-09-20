package dev.doctize.annotations;

/**
 * The names of the kinds of diagram the architecture generator draws, for {@link DiagramIgnore#types()} and
 * {@link DiagramInclude#types()}:
 *
 * <pre>{@code
 * &#64;DiagramIgnore(types = DiagramTypes.CLASSES)
 * }</pre>
 *
 * <p>They are constants, so the compiler copies the text into the annotation, and the class file of the annotated code does not
 * refer to this class. That is why the annotations take text and not an enum: a library that is compiled against an enum constant
 * makes every project that uses the library without Doctize on its class path see a compiler warning.
 *
 * <p>The names are not case sensitive.
 */
@Doc(group = "Annotations", order = 10)
public final class DiagramTypes {

    /** The overview: the packages or components of the project and how they depend on each other. */
    public static final String OVERVIEW = "overview";

    /** Class diagrams: types, their members and their relations. */
    public static final String CLASSES = "classes";

    /** State diagrams of the state machines marked with {@link StateMachine}. */
    public static final String STATES = "states";

    /** Entity-relationship diagrams of the database model. */
    public static final String ENTITIES = "entities";

    /** Flow charts of the calls that start at a method marked with {@link Flow}. */
    public static final String FLOWS = "flows";

    private DiagramTypes() {}
}
