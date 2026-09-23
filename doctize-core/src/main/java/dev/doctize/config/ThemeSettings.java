package dev.doctize.config;

import dev.doctize.annotations.Doc;
import java.util.List;
import java.util.Map;

/**
 * How the site looks.
 *
 * @param name a named theme layered over the built-in one, e.g. {@code ocean}, see {@code doctize themes}; may be empty
 * @param path directory of a custom theme layered over the built-in one (and the named theme), may be empty
 * @param accent CSS colour used for links and highlights, may be empty for the theme default
 * @param extraCss stylesheets (relative to the config file) added after the theme's own
 * @param extraJs scripts (relative to the config file) added after the theme's own
 * @param options free-form values a custom theme can read as {@code {{theme.options.key}}}
 */
@Doc(group = "Configuration", order = 22)
public record ThemeSettings(
        String name, String path, String accent, List<String> extraCss, List<String> extraJs, Map<String, String> options) {

    public ThemeSettings {
        name = name == null ? "" : name;
        path = path == null ? "" : path;
        accent = accent == null ? "" : accent;
        extraCss = extraCss == null ? List.of() : List.copyOf(extraCss);
        extraJs = extraJs == null ? List.of() : List.copyOf(extraJs);
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    public static ThemeSettings defaults() {
        return new ThemeSettings("", "", "", List.of(), List.of(), Map.of());
    }
}
