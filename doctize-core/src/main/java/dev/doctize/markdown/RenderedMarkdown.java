package dev.doctize.markdown;

import java.util.List;

/**
 * The result of rendering Markdown.
 *
 * @param html the rendered HTML fragment
 * @param toc all headings in document order
 * @param chunks the plain text split at headings, for the search index
 */
public record RenderedMarkdown(String html, List<TocEntry> toc, List<TextChunk> chunks) {}
