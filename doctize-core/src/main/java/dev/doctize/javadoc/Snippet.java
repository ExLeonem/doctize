package dev.doctize.javadoc;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Turns a {@code {@snippet}} tag into a fenced code block: inline content, or an external file (or one region of it),
 * with its markup comments applied. The URLs of {@code @link} tags travel to the code block renderer in the
 * {@code links} attribute of the fence.
 */
final class Snippet {

    private Snippet() {}

    /**
     * @param problems told about snippets that cannot be shown as asked, e.g. a missing file or region
     */
    static String toMarkdown(
            String body, SnippetSource files, LinkResolver links, Consumer<String> unresolvedLinks, Consumer<String> problems) {
        SnippetTag tag = SnippetTag.parse(body);
        Optional<String> external = tag.externalReference();
        Optional<String> text = tag.content().isPresent() ? tag.content() : external.flatMap(files::read);
        if (text.isEmpty()) {
            if (external.isPresent()) {
                problems.accept("{@snippet " + body + "}: the file was not found; external snippets are read by the annotation processor"
                        + " from the snippet-files directory of the package or the doctize.snippetPath option");
                return "";
            }
            return Markdown.code(body);
        }
        Optional<String> region = tag.attribute("region");
        if (region.isPresent()) {
            text = SnippetMarkup.region(text.get(), region.get());
            if (text.isEmpty()) {
                problems.accept("{@snippet " + body + "}: there is no region \"" + region.get() + "\"");
                return "";
            }
        }
        SnippetMarkup.Result result = SnippetMarkup.apply(text.get(), target -> {
            Optional<String> url = links.resolve(target);
            if (url.isEmpty()) {
                unresolvedLinks.accept(target);
            }
            return url;
        });
        String code = result.code().stripIndent().strip();
        return "\n```" + tag.language() + linksAttribute(result.links()) + "\n" + code + "\n```\n";
    }

    private static String linksAttribute(List<String> links) {
        return links.isEmpty()
                ? ""
                : " links=\"" + links.stream().map(url -> url.replace(" ", "%20").replace("\"", "%22")).collect(Collectors.joining(" ")) + "\"";
    }
}
