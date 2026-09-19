package dev.doctize.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Publishes a type as a page of the API reference.
 *
 * <p>The Javadoc comment of the annotated type, and of its public and protected members, becomes
 * the page content. Comments are written in Markdown (JEP 467 {@code ///} comments work best, but
 * classic {@code /** *}{@code /} comments are supported too) and may use the standard Javadoc
 * tags such as {@code @param}, {@code @return}, {@code @throws}, {@code @since}, {@code @see} and
 * {@code {@link ...}}. Unknown block tags are rendered as labelled blocks.
 *
 * <pre>{@code
 * /// A cache with a bounded size.
 * ///
 * /// @since 1.2
 * @Doc(section = "Caching", order = 10)
 * public interface Cache<K, V> { ... }
 * }</pre>
 *
 * <p>The annotations have source retention: they are read while your code compiles and leave no trace
 * in class files, so libraries that use them impose no dependency on their users.
 *
 * @see DocPage
 * @see DocIgnore
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Doc(group = "Annotations", order = 1)
public @interface Doc {

    /**
     * The section the page is listed in, given either as its id or its title.
     * Sections that are not declared in the site configuration are created on demand.
     * When empty, the configured API section is used ({@code "API Reference"} by default).
     */
    String section() default "";

    /** The page title. Defaults to the simple name of the type. */
    String title() default "";

    /**
     * Sorting key: lower values come first, ties are broken by title. Pages that share a {@link #group() group}
     * stay together, and groups are ordered by the lowest {@code order} among their pages.
     */
    int order() default 100;

    /** Label used to group pages in the navigation. Defaults to the package name. */
    String group() default "";

    /** Extra terms that should find this page through search, e.g. synonyms. */
    String[] keywords() default {};

    /** Whether the public and protected members of the type are documented as well. */
    boolean members() default true;
}
