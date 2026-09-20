package dev.doctize.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A change from one state of a {@link StateMachine} to another. Repeat it for several changes.
 *
 * <p>On an enum constant, the constant is the state the change starts from, and {@link #from()} is not needed. On a method,
 * {@link #from()} names the states it can be called in, and {@link #machine()} the enum, unless the method is in the enum
 * itself.
 *
 * <p>Like {@link DiagramIgnore}, it is kept in the class file and not needed at run time.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD})
@Repeatable(Transitions.class)
@Doc(group = "Annotations", order = 14)
public @interface Transition {

    /**
     * The states the change starts from, as the names of enum constants; {@code "*"} for every state. Needed on methods, not on
     * constants.
     */
    String[] from() default {};

    /** The state the change leads to, the name of an enum constant. */
    String to();

    /** What causes the change: an event, a command, the name of an operation. Shown on the arrow. */
    String on() default "";

    /** The enum of the {@link StateMachine} for a method outside of it. */
    Class<?> machine() default Void.class;
}
