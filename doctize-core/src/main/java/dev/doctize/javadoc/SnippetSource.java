package dev.doctize.javadoc;

import java.util.Map;
import java.util.Optional;

/** The content of the external files that {@code {@snippet file=...}} and {@code {@snippet class=...}} tags show. */
@FunctionalInterface
public interface SnippetSource {

    /**
     * @param reference {@code file:<path>} or {@code class:<name>}, see {@link SnippetTag#externalReference()}
     * @return the content of the file, empty if it is not known
     */
    Optional<String> read(String reference);

    static SnippetSource none() {
        return reference -> Optional.empty();
    }

    static SnippetSource of(Map<String, String> files) {
        return reference -> Optional.ofNullable(files.get(reference));
    }
}
