package dev.doctize.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the start of a flow, which the architecture generator draws as a flow chart: the methods it calls, and the
 * ones they call, in the order the calls are made, as far as the configuration says ({@code flows.depth}).
 *
 * <p>Like {@link DiagramIgnore}, it is kept in the class file and not needed at run time.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Doc(group = "Annotations", order = 16)
public @interface Flow {

    /** The title of the flow chart. Default: the name of the class and method. */
    String name() default "";

    /** What happens in this flow, shown above its chart. */
    String description() default "";
}
