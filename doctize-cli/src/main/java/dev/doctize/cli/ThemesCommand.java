package dev.doctize.cli;

import dev.doctize.theme.NamedTheme;
import dev.doctize.theme.Theme;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/** {@code doctize themes}: lists the themes that {@code theme.name} can choose. */
@Command(name = "themes", description = "Lists the themes that theme.name or --theme-name can choose.", mixinStandardHelpOptions = true)
final class ThemesCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        List<NamedTheme> themes = Theme.available();
        int width = themes.stream().mapToInt(theme -> theme.name().length()).max().orElse(0);
        for (NamedTheme theme : themes) {
            System.out.printf("%-" + width + "s  %s%n", theme.name(), theme.description());
        }
        return 0;
    }
}
