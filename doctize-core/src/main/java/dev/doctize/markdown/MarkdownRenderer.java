package dev.doctize.markdown;

import dev.doctize.text.Slugs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Renders GitHub-flavoured Markdown (tables, alerts, task lists, strikethrough, autolinks) to HTML.
 *
 * <p>Headings receive stable ids, code blocks are highlighted, and links can be rewritten. Alongside
 * the HTML it reports the headings and the per-heading plain text needed for navigation and search.
 * Instances are immutable and may be shared.
 */
public final class MarkdownRenderer {

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AlertsExtension.create(),
            AutolinkExtension.create(),
            TaskListItemsExtension.create());

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
    private final CodeHighlighter highlighter;

    public MarkdownRenderer(CodeHighlighter highlighter) {
        this.highlighter = highlighter;
    }

    /** Renders with the default highlighter and without link rewriting. */
    public RenderedMarkdown render(String markdown) {
        return render(markdown, "", LinkRewriter.identity());
    }

    /**
     * @param idPrefix prepended to heading ids to keep them unique when several fragments share a page
     * @param links rewrites link and image destinations
     */
    public RenderedMarkdown render(String markdown, String idPrefix, LinkRewriter links) {
        Node document = parser.parse(markdown);
        Map<Heading, String> ids = assignHeadingIds(document, idPrefix);
        HtmlRenderer renderer = HtmlRenderer.builder()
                .extensions(EXTENSIONS)
                .nodeRendererFactory(context -> new HeadingRenderer(context, ids))
                .nodeRendererFactory(context -> new CodeBlockRenderer(context, highlighter))
                .attributeProviderFactory(context -> (node, tag, attributes) -> rewriteLinks(node, attributes, links))
                .build();
        return new RenderedMarkdown(renderer.render(document), tableOfContents(ids, document), chunks(document, ids));
    }

    private static Map<Heading, String> assignHeadingIds(Node document, String idPrefix) {
        Map<Heading, String> ids = new IdentityHashMap<>();
        Set<String> taken = new HashSet<>();
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                String base = idPrefix + Slugs.slugify(PlainText.of(heading));
                String id = base;
                for (int suffix = 2; !taken.add(id); suffix++) {
                    id = base + "-" + suffix;
                }
                ids.put(heading, id);
            }
        }
        return ids;
    }

    private static void rewriteLinks(Node node, Map<String, String> attributes, LinkRewriter links) {
        if (node instanceof Link link) {
            String destination = links.link(link.getDestination());
            attributes.put("href", destination);
            if (isExternal(destination)) {
                attributes.put("class", "external");
                attributes.put("rel", "noopener");
            }
        } else if (node instanceof Image image) {
            attributes.put("src", links.image(image.getDestination()));
            attributes.put("loading", "lazy");
        }
    }

    private static boolean isExternal(String destination) {
        return destination.startsWith("http://") || destination.startsWith("https://") || destination.startsWith("//");
    }

    private static List<TocEntry> tableOfContents(Map<Heading, String> ids, Node document) {
        List<TocEntry> entries = new ArrayList<>();
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                entries.add(new TocEntry(heading.getLevel(), PlainText.of(heading), ids.get(heading)));
            }
        }
        return entries;
    }

    private static List<TextChunk> chunks(Node document, Map<Heading, String> ids) {
        List<TextChunk> chunks = new ArrayList<>();
        String headingId = "";
        String headingText = "";
        StringBuilder text = new StringBuilder();
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                addChunk(chunks, headingId, headingText, text);
                headingId = ids.get(heading);
                headingText = PlainText.of(heading);
                text.setLength(0);
            } else {
                text.append(PlainText.of(node)).append(' ');
            }
        }
        addChunk(chunks, headingId, headingText, text);
        return chunks;
    }

    private static void addChunk(List<TextChunk> chunks, String headingId, String heading, StringBuilder text) {
        String plain = text.toString().strip();
        if (!plain.isEmpty() || !heading.isEmpty()) {
            chunks.add(new TextChunk(headingId, heading, plain));
        }
    }
}
