package dev.doctize.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Turns the Javadoc comment of a package or type into a free-form guide page.
 *
 * <p>Use it for prose such as a "Quick start" or a "Setup" chapter that you prefer to keep next to
 * the code, typically in a {@code package-info.java} file. Unlike {@link Doc}, no member listing is
 * generated: the comment <em>is</em> the page.
 *
 * <pre>{@code
 * /// # Quick start
 * ///
 * /// Create a cache and store your first value ...
 * @DocPage(section = "Quick start", order = 1)
 * package com.acme.cache;
 * }</pre>
 *
 * @see Doc
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Doc(group = "Annotations", order = 2)
public @interface DocPage {

    /** The section the page is listed in, given either as its id or its title. */
    String section() default "";

    /** The page title. Defaults to the name of the annotated element. */
    String title() default "";

    /**
     * Sorting key: lower values come first, ties are broken by title. Pages that share a {@link #group() group}
     * stay together, and groups are ordered by the lowest {@code order} among their pages.
     */
    int order() default 100;

    /** Label used to group pages in the navigation. */
    String group() default "";

    /** Extra terms that should find this page through search, e.g. synonyms. */
    String[] keywords() default {};
}
