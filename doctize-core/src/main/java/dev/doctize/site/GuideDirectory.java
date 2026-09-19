package dev.doctize.site;

import dev.doctize.DoctizeException;
import dev.doctize.markdown.TitledMarkdown;
import dev.doctize.model.Placement;
import dev.doctize.text.Slugs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A directory of Markdown guides.
 *
 * <ul>
 *   <li>Every {@code .md} file is a page; {@code index.md} in the root is the home page introduction.
 *   <li>A sub-directory is a section: {@code setup/install.md} lives in the section "Setup".
 *   <li>A numeric prefix ({@code 01-quick-start.md}, {@code 02-setup/}) is used for ordering and dropped from names.
 *   <li>Front matter ({@code title}, {@code section}, {@code order}, {@code group}, {@code slug},
 *       {@code description}, {@code keywords}) overrides all of the above.
 *   <li>Every other file (images ...) is published as an asset.
 * </ul>
 */
public final class GuideDirectory implements PageSource {

    private static final Pattern ORDER_PREFIX = Pattern.compile("^(\\d+)[-_.](.+)$");
    private static final String INTRO_FILE = "index.md";

    private final Path root;

    public GuideDirectory(Path root) {
        this.root = root;
    }

    @Override
    public List<Page> pages() {
        return markdownFiles().stream().filter(file -> !isIntro(file)).map(this::pageOf).toList();
    }

    /** The Markdown introduction for the home page, empty if there is no {@code index.md}. */
    public String intro() {
        Path file = root.resolve(INTRO_FILE);
        return Files.isRegularFile(file) ? FrontMatter.parse(read(file), file.toString()).body().strip() : "";
    }

    /** All non-Markdown files below the directory, e.g. images referenced by the guides. */
    public List<Path> assets() {
        return files().stream().filter(file -> !isMarkdown(file)).toList();
    }

    public Path root() {
        return root;
    }

    private Page pageOf(Path file) {
        Path relative = root.relativize(file);
        FrontMatter meta = FrontMatter.parse(read(file), relative.toString());
        TitledMarkdown text = TitledMarkdown.split(meta.body().strip());
        Ordered stem = withoutOrderPrefix(stripExtension(file.getFileName().toString()));
        String title = firstNonBlank(meta.string("title"), text.title().orElse(""), Slugs.humanize(stem.name()));
        return new Page(
                firstNonBlank(meta.string("slug"), stem.name()),
                title,
                firstNonBlank(meta.string("section"), sectionOf(relative)),
                meta.string("group"),
                meta.integer("order", stem.order()),
                meta.string("description"),
                meta.strings("keywords"),
                new PageContent.Guide(text.body(), relative.toString().replace('\\', '/')));
    }

    private static String sectionOf(Path relative) {
        return relative.getNameCount() > 1 ? withoutOrderPrefix(relative.getName(0).toString()).name() : "";
    }

    private boolean isIntro(Path file) {
        return root.relativize(file).toString().equals(INTRO_FILE);
    }

    private static boolean isMarkdown(Path file) {
        return file.getFileName().toString().endsWith(".md");
    }

    private List<Path> markdownFiles() {
        return files().stream().filter(GuideDirectory::isMarkdown).toList();
    }

    private List<Path> files() {
        if (!Files.isDirectory(root)) {
            throw new DoctizeException("Guides directory does not exist: " + root);
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new DoctizeException("Cannot read guides directory " + root + ": " + e.getMessage(), e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new DoctizeException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    private static String stripExtension(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    private static Ordered withoutOrderPrefix(String name) {
        Matcher matcher = ORDER_PREFIX.matcher(name);
        return matcher.matches()
                ? new Ordered(matcher.group(2), Integer.parseInt(matcher.group(1)))
                : new Ordered(name, Placement.DEFAULT_ORDER);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private record Ordered(String name, int order) {}
}
