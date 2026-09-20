package dev.doctize.architecture;

import dev.doctize.annotations.Doc;
import dev.doctize.config.ArchitectureSettings;
import dev.doctize.site.Page;
import dev.doctize.site.PageContent;
import dev.doctize.site.PageSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Contributes the architecture page: a map of the packages of the analyzed classes and how they depend on each other.
 *
 * <p>Problems found while analyzing, such as dependency cycles and broken rules, are available as {@link #warnings()} once
 * {@link #pages()} was called, so that a build can fail on them.
 */
@Doc(group = "Architecture", order = 72)
public final class ArchitecturePageSource implements PageSource {

    private final ArchitectureSettings settings;
    private final List<Path> classRoots;
    private List<Page> pages;
    private final List<String> warnings = new ArrayList<>();

    /**
     * @param settings how to generate the map
     * @param classRoots directories and jars with compiled classes
     */
    public ArchitecturePageSource(ArchitectureSettings settings, List<Path> classRoots) {
        this.settings = settings;
        this.classRoots = List.copyOf(classRoots);
    }

    @Override
    public List<Page> pages() {
        if (pages == null) {
            pages = build();
        }
        return pages;
    }

    /** Problems found in the architecture; complete after {@link #pages()}. */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    /** A reason from the configuration may or may not end in a full stop already. */
    private static String sentence(String text) {
        return text.endsWith(".") || text.endsWith("!") || text.endsWith("?") ? text : text + ".";
    }

    private List<Page> build() {
        ArchitectureModel model = new ArchitectureAnalyzer(settings).analyze(classRoots);
        if (model.isEmpty()) {
            warnings.add("The architecture map is empty: no classes were found in " + (classRoots.isEmpty() ? "any location (pass the compiled classes with --classes)" : classRoots)
                    + (settings.include().isEmpty() && settings.exclude().isEmpty() && settings.rootPackage().isEmpty() ? "" : ", or include, exclude and rootPackage leave none")
                    + ".");
            return List.of();
        }
        model.cycles().forEach(cycle -> warnings.add("Architecture: dependency cycle between " + String.join(", ", cycle) + "."));
        model.violations().forEach(violation -> warnings.add("Architecture: " + sentence(violation.message())));
        String markdown = new ArchitecturePage(model, settings).markdown();
        return List.of(new Page(
                "architecture",
                settings.title(),
                settings.section(),
                "",
                settings.order(),
                "How the packages depend on each other: the map, cycles, layers and rules.",
                List.of("architecture", "dependencies", "packages", "layers", "cycles", "modules"),
                new PageContent.Guide(markdown, "")));
    }
}
