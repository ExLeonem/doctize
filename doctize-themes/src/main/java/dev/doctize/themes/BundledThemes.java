package dev.doctize.themes;

import dev.doctize.theme.NamedTheme;
import dev.doctize.theme.ThemeProvider;
import java.util.List;

/**
 * The themes of {@code doctize-themes}. Each is a stylesheet over the built-in theme, with a light and a dark palette, so
 * search, navigation and the dark mode toggle work the same in all of them.
 */
public final class BundledThemes implements ThemeProvider {

    private static final String BASE = "dev/doctize/themes/";

    @Override
    public List<NamedTheme> themes() {
        return List.of(
                theme("ocean", "Teal and deep blue, cool neutrals and rounder corners."),
                theme("forest", "Moss green on warm off-white; earthy in the dark."),
                theme("paper", "A serif reading theme with a narrow text column, like a printed book."),
                theme("contrast", "High contrast for accessibility: black on white, underlined links, strong focus rings."),
                theme("terminal", "Monospace everywhere, square corners and a green prompt."));
    }

    private static NamedTheme theme(String name, String description) {
        return new NamedTheme(name, description, BASE + name, List.of("css/themes/" + name + ".css"));
    }
}
