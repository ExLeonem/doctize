package dev.doctize.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Excludes a member of a {@link Doc documented type} from the generated documentation. */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({
    ElementType.METHOD,
    ElementType.CONSTRUCTOR,
    ElementType.FIELD,
    ElementType.TYPE,
})
@Doc(group = "Annotations", order = 3)
public @interface DocIgnore {}
