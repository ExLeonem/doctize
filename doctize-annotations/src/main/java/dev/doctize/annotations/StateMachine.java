package dev.doctize.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an enum as the states of a state machine, which the architecture generator draws as a state diagram. The constants of the
 * enum are the states; the changes between them are declared with {@link Transition}, on the constants or on the methods that make
 * the change.
 *
 * <pre>{@code
 * @StateMachine(initial = "NEW", terminal = {"SHIPPED", "CANCELLED"})
 * enum OrderState {
 *     @Transition(to = "PAID", on = "pay") @Transition(to = "CANCELLED", on = "cancel") NEW,
 *     @Transition(to = "SHIPPED", on = "ship") PAID,
 *     SHIPPED,
 *     CANCELLED
 * }
 * }</pre>
 *
 * <p>Like {@link DiagramIgnore}, it is kept in the class file and not needed at run time.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Doc(group = "Annotations", order = 13)
public @interface StateMachine {

    /** The name of the state machine in the diagram. Default: the name of the enum. */
    String name() default "";

    /** The state a new object is in; the diagram starts there. Empty for no start marker. */
    String initial() default "";

    /** States that are the end of the state machine; the diagram ends there. */
    String[] terminal() default {};

    /** What the state machine is for, shown above its diagram. */
    String description() default "";
}
