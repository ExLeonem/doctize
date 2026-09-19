package dev.doctize.markdown;

import java.util.Map;
import java.util.Set;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

/** Renders headings with an id and a permalink anchor. */
final class HeadingRenderer implements NodeRenderer {

    private final HtmlNodeRendererContext context;
    private final Map<Heading, String> ids;

    HeadingRenderer(HtmlNodeRendererContext context, Map<Heading, String> ids) {
        this.context = context;
        this.ids = ids;
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(Heading.class);
    }

    @Override
    public void render(Node node) {
        Heading heading = (Heading) node;
        String id = ids.get(heading);
        String tag = "h" + heading.getLevel();
        HtmlWriter html = context.getWriter();
        html.line();
        html.tag(tag, context.extendAttributes(heading, tag, Map.of("id", id)));
        for (Node child = heading.getFirstChild(); child != null; child = child.getNext()) {
            context.render(child);
        }
        html.tag("a", Map.of("class", "anchor", "href", "#" + id, "aria-label", "Permalink"));
        html.text("#");
        html.tag("/a");
        html.tag("/" + tag);
        html.line();
    }
}
