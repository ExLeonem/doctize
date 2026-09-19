package dev.doctize;

import dev.doctize.annotations.Doc;
import java.nio.file.Path;
import java.util.List;

/**
 * The outcome of a generation run.
 *
 * @param pages number of pages generated, excluding home and error pages
 * @param sections number of sections in the navigation
 * @param outputDirectory where the site was written
 * @param warnings problems that did not stop generation, such as {@code {@link}} tags that point nowhere
 */
@Doc(group = "Generator", order = 12)
public record GenerationReport(int pages, int sections, Path outputDirectory, List<String> warnings) {

    public GenerationReport {
        warnings = List.copyOf(warnings);
    }
}
