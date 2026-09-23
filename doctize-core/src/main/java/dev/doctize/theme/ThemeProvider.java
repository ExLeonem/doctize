package dev.doctize.theme;

import dev.doctize.annotations.Doc;
import java.util.List;

/**
 * Offers {@linkplain NamedTheme named themes}. Implementations are found with {@link java.util.ServiceLoader}: a jar that
 * lists its provider in {@code META-INF/services/dev.doctize.theme.ThemeProvider} makes its themes available as soon as it
 * is on the class path, which is how {@code dev.doctize:doctize-themes} works.
 */
@Doc(group = "Theming", order = 52)
public interface ThemeProvider {

    List<NamedTheme> themes();
}
