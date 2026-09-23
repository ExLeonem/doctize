package dev.doctize.theme;

import dev.doctize.annotations.Doc;
import java.util.List;

/**
 * A theme that can be chosen by name, with {@code theme.name} in {@code doctize.yml}. It is layered over the built-in theme,
 * like a custom theme directory, so it only contains what it changes: usually a stylesheet that sets the custom properties
 * of the built-in one.
 *
 * @param name how the theme is chosen, e.g. {@code ocean}; lower case letters, digits and dashes
 * @param description one line for {@code doctize themes}
 * @param resources the classpath directory with the theme's {@code templates/} and {@code assets/}, read with the class
 *     loader of the {@link ThemeProvider} that offers the theme
 * @param stylesheets stylesheets below {@code assets/}, e.g. {@code css/themes/ocean.css}, that every page loads after the
 *     built-in stylesheet and before the site's own {@code extraCss}
 */
@Doc(group = "Theming", order = 51)
public record NamedTheme(String name, String description, String resources, List<String> stylesheets) {

    /** The name of the built-in theme, which is used when no theme is named. */
    public static final String DEFAULT = "default";

    public NamedTheme {
        if (name == null || !name.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("A theme name consists of lower case letters, digits and dashes: " + name);
        }
        description = description == null ? "" : description;
        stylesheets = stylesheets == null ? List.of() : List.copyOf(stylesheets);
    }
}
