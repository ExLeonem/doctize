package dev.doctize.theme;

import java.util.List;

/** Registered in the test resources, as a jar of themes would be. */
public final class TestThemeProvider implements ThemeProvider {

    @Override
    public List<NamedTheme> themes() {
        return List.of(
                new NamedTheme("sunset", "Orange, for tests.", "test-themes/sunset", List.of("css/themes/sunset.css")),
                new NamedTheme("default", "Cannot replace the built-in theme.", "test-themes/sunset", List.of()));
    }
}
