package dev.doctize;

import dev.doctize.annotations.Doc;
import dev.doctize.license.License;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of a generation run.
 *
 * @param pages number of pages generated, excluding home and error pages
 * @param sections number of sections in the navigation
 * @param outputDirectory where the site was written
 * @param warnings problems that did not stop generation, such as {@code {@link}} tags that point nowhere, or a licensed
 *     feature that was asked for without a license
 * @param license the valid license that was found, if any
 */
@Doc(group = "Generator", order = 12)
public record GenerationReport(
        int pages, int sections, Path outputDirectory, List<String> warnings, Optional<License> license) {

    public GenerationReport {
        warnings = List.copyOf(warnings);
    }

    /** A report of a run without a license. */
    public GenerationReport(int pages, int sections, Path outputDirectory, List<String> warnings) {
        this(pages, sections, outputDirectory, warnings, Optional.empty());
    }
}
