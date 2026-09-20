package dev.doctize.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keeps a type, a member or a whole package out of the generated architecture diagrams.
 *
 * <p>By default it applies to every kind of diagram; name some in {@link #types()} to limit it. On a type it hides the type and
 * everything about it; on a field or method it hides just that member, and with it the relation or call that comes from it.
 * On a package (in {@code package-info.java}) it hides all types of the package.
 *
 * <p>If a code part is marked with both this and {@link DiagramInclude}, this one wins.
 *
 * <p>Unlike {@link Doc}, this annotation is kept in the class file, because the diagrams are made from compiled classes.
 * It is not needed at run time: the JVM ignores annotations whose class it cannot find, so declare the dependency as
 * {@code compileOnly}.
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
@Doc(group = "Annotations", order = 11)
public @interface DiagramIgnore {

    /**
     * The diagrams this applies to, by the names in {@link DiagramTypes}. Empty for all of them.
     *
     * <p>This is text and not an enum on purpose: see {@link DiagramTypes}.
     */
    String[] types() default {};
}
