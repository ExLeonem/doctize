package dev.doctize.theme;

import dev.doctize.DoctizeException;
import dev.doctize.annotations.Doc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.TreeMap;

/**
 * The templates and static assets that give the site its look.
 *
 * <p>A theme is a stack of layers. The built-in theme is always the bottom layer; a {@linkplain NamedTheme named theme}
 * and a custom theme directory are put on top of it, in this order, so each only needs to contain the files it changes:
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

    private static final String THEMES_ARTIFACT = "dev.doctize:doctize-themes";

    private final List<ThemeSource> layers;
    private final List<String> stylesheets;

    private Theme(List<ThemeSource> layers, List<String> stylesheets) {
        this.layers = List.copyOf(layers);
        this.stylesheets = List.copyOf(stylesheets);
    }

    /** The built-in default theme. */
    public static Theme builtIn() {
        return of(null, null);
    }

    /** A custom theme directory layered over the built-in theme. */
    public static Theme customized(Path directory) {
        return of(null, directory);
    }

    /**
     * The built-in theme, with the named theme and the custom theme directory on top of it.
     *
     * @param name a theme of {@link #available()}; {@code null}, blank or {@code default} for the built-in theme alone
     * @param directory a custom theme directory, or {@code null}
     */
    public static Theme of(String name, Path directory) {
        List<ThemeSource> layers = new ArrayList<>();
        List<String> stylesheets = new ArrayList<>();
        if (directory != null) {
            if (!Files.isDirectory(directory)) {
                throw new DoctizeException("Theme directory does not exist: " + directory);
            }
            layers.add(new DirectoryThemeSource(directory));
        }
        if (name != null && !name.isBlank() && !name.equals(NamedTheme.DEFAULT)) {
            Provided named = provided().get(name);
            if (named == null) {
                throw new DoctizeException("Unknown theme '" + name + "'. Available: " + String.join(", ", names())
                        + ". Themes other than " + NamedTheme.DEFAULT + " come from " + THEMES_ARTIFACT
                        + ", or another jar with a ThemeProvider, on the class path.");
            }
            layers.add(new ClasspathThemeSource(named.loader(), named.theme().resources()));
            named.theme().stylesheets().forEach(path -> stylesheets.add("assets/" + path));
        }
        layers.add(builtInSource());
        return new Theme(layers, stylesheets);
    }

    /** The themes that can be named: the built-in one and those of every {@link ThemeProvider} on the class path, by name. */
    public static List<NamedTheme> available() {
        List<NamedTheme> themes = new ArrayList<>();
        themes.add(new NamedTheme(NamedTheme.DEFAULT, "The built-in theme: indigo accents, system fonts.", BUILT_IN_BASE, List.of()));
        provided().values().stream().map(Provided::theme).forEach(themes::add);
        return themes;
    }

    /** The stylesheets of a named theme, as URLs relative to the site root, in the order a page loads them. */
    public List<String> stylesheets() {
        return stylesheets;
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

    private static List<String> names() {
        return available().stream().map(NamedTheme::name).toList();
    }

    /** The first provider to offer a name wins, so a theme cannot be replaced by accident by a later jar. */
    private static Map<String, Provided> provided() {
        Map<String, Provided> themes = new TreeMap<>();
        for (ThemeProvider provider : ServiceLoader.load(ThemeProvider.class, Theme.class.getClassLoader())) {
            for (NamedTheme theme : provider.themes()) {
                if (!theme.name().equals(NamedTheme.DEFAULT)) {
                    themes.putIfAbsent(theme.name(), new Provided(theme, provider.getClass().getClassLoader()));
                }
            }
        }
        return themes;
    }

    private record Provided(NamedTheme theme, ClassLoader loader) {}

    private static ThemeSource builtInSource() {
        return new ClasspathThemeSource(Theme.class.getClassLoader(), BUILT_IN_BASE);
    }
}
