package dev.doctize.render;

import dev.doctize.javadoc.InlineTagProcessor;
import dev.doctize.markdown.LinkRewriter;
import dev.doctize.markdown.MarkdownRenderer;
import dev.doctize.markdown.RenderedMarkdown;
import dev.doctize.markdown.TextChunk;
import java.util.stream.Collectors;

/**
 * Renders documentation text for one page: Javadoc inline tags first, then Markdown. Links are
 * resolved from the point of view of that page.
 */
final class Fragments {

    private final MarkdownRenderer markdown;
    private final InlineTagProcessor inlineTags;
    private final LinkRewriter links;

    Fragments(MarkdownRenderer markdown, InlineTagProcessor inlineTags, LinkRewriter links) {
        this.markdown = markdown;
        this.inlineTags = inlineTags;
        this.links = links;
    }

    RenderedMarkdown render(String text, String idPrefix) {
        return markdown.render(inlineTags.process(text), idPrefix, links);
    }

    /** Block-level HTML, e.g. for a description that may have several paragraphs. */
    String html(String text, String idPrefix) {
        return text.isBlank() ? "" : render(text, idPrefix).html().strip();
    }

    /** HTML for use inside running text or table cells: the wrapping paragraph is removed. */
    String inlineHtml(String text) {
        String html = html(text, "");
        boolean singleParagraph = html.startsWith("<p>") && html.indexOf("<p>", 1) < 0 && html.endsWith("</p>");
        return singleParagraph ? html.substring(3, html.length() - 4) : html;
    }

    /** The text without markup, for the search index. */
    String plain(String text) {
        return text.isBlank()
                ? ""
                : render(text, "").chunks().stream().map(TextChunk::text).collect(Collectors.joining(" "));
    }
}
