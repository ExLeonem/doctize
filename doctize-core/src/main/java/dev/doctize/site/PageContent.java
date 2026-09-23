package dev.doctize.site;

import dev.doctize.annotations.Doc;
import dev.doctize.model.SourceElement;
import java.util.Map;

/** What a page shows: hand-written Markdown, or the reference of a documented type. */
@Doc(group = "Extending", order = 32)
public sealed interface PageContent {

    /**
     * Markdown prose, possibly containing Javadoc inline tags.
     *
     * @param markdown the body without front matter and without the leading title heading
     * @param sourcePath path of the source file relative to the guides directory, using {@code /};
     *     empty if the text comes from a Javadoc comment. Used to resolve relative links.
     * @param snippets the external files of the {@code {@snippet}} tags in a Javadoc comment, see {@link SourceElement}
     */
    record Guide(String markdown, String sourcePath, Map<String, String> snippets) implements PageContent {

        public Guide(String markdown, String sourcePath) {
            this(markdown, sourcePath, Map.of());
        }

        public Guide {
            snippets = snippets == null ? Map.of() : Map.copyOf(snippets);
        }
    }

    /** The API reference of one documented type. */
    record Type(SourceElement element) implements PageContent {}
}
