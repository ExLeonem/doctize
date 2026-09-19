package dev.doctize.markdown;

import dev.doctize.text.Html;
import java.util.Set;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

/**
 * Renders fenced code blocks with syntax highlighting, an optional title and tab grouping.
 * Consecutive blocks that carry a {@code tab="..."} attribute share one tab container.
 */
final class CodeBlockRenderer implements NodeRenderer {

    private final HtmlWriter html;
    private final CodeHighlighter highlighter;

    CodeBlockRenderer(HtmlNodeRendererContext context, CodeHighlighter highlighter) {
        this.html = context.getWriter();
        this.highlighter = highlighter;
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(FencedCodeBlock.class);
    }

    @Override
    public void render(Node node) {
        FencedCodeBlock block = (FencedCodeBlock) node;
        FenceInfo info = FenceInfo.parse(block.getInfo());
        boolean tabbed = info.isTabbed();
        html.line();
        if (tabbed && !isTabbed(block.getPrevious())) {
            html.raw("<div class=\"tabs\">");
        }
        if (tabbed) {
            html.raw("<div class=\"tab-panel\" data-tab=\"" + Html.escape(info.tab()) + "\">");
        }
        html.raw(codeBlock(block, info));
        if (tabbed) {
            html.raw("</div>");
        }
        if (tabbed && !isTabbed(block.getNext())) {
            html.raw("</div>");
        }
        html.line();
    }

    private String codeBlock(FencedCodeBlock block, FenceInfo info) {
        String language = info.language();
        StringBuilder out = new StringBuilder("<div class=\"code-block\" data-lang=\"")
                .append(Html.escape(language))
                .append("\">");
        if (!info.title().isEmpty()) {
            out.append("<div class=\"code-title\">").append(Html.escape(info.title())).append("</div>");
        }
        String code = block.getLiteral().stripTrailing();
        out.append("<pre><code");
        if (!language.isEmpty()) {
            out.append(" class=\"language-").append(Html.escape(language)).append('"');
        }
        return out.append('>').append(highlighter.highlight(code, language)).append("</code></pre></div>").toString();
    }

    private static boolean isTabbed(Node node) {
        return node instanceof FencedCodeBlock fenced && FenceInfo.parse(fenced.getInfo()).isTabbed();
    }
}
