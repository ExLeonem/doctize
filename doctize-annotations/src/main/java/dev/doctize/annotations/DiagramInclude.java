package dev.doctize.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes sure a type, a member or a whole package is part of the generated architecture diagrams, whatever the configuration
 * would otherwise leave out: a package that is excluded, a member that is less visible than {@code visibility}, an accessor.
 * Where the configuration says {@code onlyMarked: true}, only what is marked with this annotation is shown.
 *
 * <p>By default it applies to every kind of diagram; name some in {@link #types()} to limit it. With {@link DiagramTypes#ENTITIES}
 * on a type, the type is an entity of the entity-relationship diagram even if it has no persistence annotation, and its fields are
 * its columns.
 *
 * <p>If a code part is marked with both this and {@link DiagramIgnore}, the ignore wins.
 *
 * <p>Like {@link DiagramIgnore}, it is kept in the class file and not needed at run time.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.CONSTRUCTOR,
    ElementType.FIELD,
    ElementType.PACKAGE,
})
@Doc(group = "Annotations", order = 12)
public @interface DiagramInclude {

    /**
     * The diagrams this applies to, by the names in {@link DiagramTypes}. Empty for all of them.
     *
     * <p>This is text and not an enum on purpose: see {@link DiagramTypes}.
     */
    String[] types() default {};
}
