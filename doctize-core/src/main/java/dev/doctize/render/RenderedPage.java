package dev.doctize.render;

import dev.doctize.markdown.TextChunk;
import dev.doctize.markdown.TocEntry;
import dev.doctize.site.SitePage;
import java.util.List;

/**
 * A page after its content was rendered, but before it is wrapped in the site layout.
 *
 * @param page the page and where it lives
 * @param html the rendered content
 * @param toc headings for the "on this page" navigation
 * @param chunks the searchable text
 * @param summary a short plain-text description
 */
public record RenderedPage(SitePage page, String html, List<TocEntry> toc, List<TextChunk> chunks, String summary) {}
