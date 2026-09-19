package dev.doctize.theme;

import dev.doctize.DoctizeException;
import dev.doctize.annotations.Doc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The templates and static assets that give the site its look.
 *
 * <p>A theme is a stack of layers. The built-in theme is always the bottom layer; a custom theme
 * directory is put on top of it, so a custom theme only needs to contain the files it changes:
 *
 * <pre>
 * my-theme/
 *   templates/layout.mustache          replaces the page frame
 *   templates/partials/footer.mustache replaces the footer only
 *   assets/css/theme.css               replaces the built-in stylesheet
 *   assets/img/logo.svg                adds a new asset
 * </pre>
 */
@Doc(group = "Theming", order = 50)
public final class Theme {

    private static final String BUILT_IN_BASE = "dev/doctize/theme/default";

    private final List<ThemeSource> layers;

    private Theme(List<ThemeSource> layers) {
        this.layers = List.copyOf(layers);
    }

    /** The built-in default theme. */
    public static Theme builtIn() {
        return new Theme(List.of(builtInSource()));
    }

    /** A custom theme directory layered over the built-in theme. */
    public static Theme customized(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new DoctizeException("Theme directory does not exist: " + directory);
        }
        return new Theme(List.of(new DirectoryThemeSource(directory), builtInSource()));
    }

    /** The source of a template, e.g. {@code "layout"} or {@code "partials/header"}. */
    public String template(String name) {
        String path = "templates/" + name + ".mustache";
        return layers.stream()
                .map(layer -> layer.read(path))
                .flatMap(Optional::stream)
                .findFirst()
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .orElseThrow(() -> new DoctizeException("The theme has no template '" + name + "' (" + path + ")"));
    }

    /** All static assets keyed by their path below {@code assets/}; upper layers win. */
    public Map<String, byte[]> assets() {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (ThemeSource layer : layers.reversed()) {
            for (String path : layer.list("assets")) {
                layer.read("assets/" + path).ifPresent(bytes -> assets.put(path, bytes));
            }
        }
        return assets;
    }

    private static ThemeSource builtInSource() {
        return new ClasspathThemeSource(Theme.class.getClassLoader(), BUILT_IN_BASE);
    }
}
