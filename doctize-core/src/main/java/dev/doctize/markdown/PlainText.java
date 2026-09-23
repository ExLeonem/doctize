package dev.doctize.markdown;

import dev.doctize.text.Html;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;

/** Extracts the readable text of a Markdown node, without any markup. */
final class PlainText extends AbstractVisitor {

    private final StringBuilder text = new StringBuilder();

    static String of(Node node) {
        PlainText visitor = new PlainText();
        node.accept(visitor);
        return visitor.text.toString().replaceAll("\\s+", " ").strip();
    }

    @Override
    public void visit(Text node) {
        text.append(node.getLiteral());
    }

    @Override
    public void visit(Code node) {
        text.append(node.getLiteral());
    }

    @Override
    public void visit(SoftLineBreak node) {
        text.append(' ');
    }

    @Override
    public void visit(HardLineBreak node) {
        text.append(' ');
    }

    @Override
    public void visit(FencedCodeBlock node) {
        text.append(CodeMarks.strip(node.getLiteral())).append(' ');
    }

    @Override
    public void visit(IndentedCodeBlock node) {
        text.append(node.getLiteral()).append(' ');
    }

    @Override
    public void visit(HtmlBlock node) {
        text.append(Html.unescape(node.getLiteral().replaceAll("<[^>]*>", " "))).append(' ');
    }

    @Override
    public void visit(Paragraph node) {
        visitChildren(node);
        text.append(' ');
    }

    @Override
    public void visit(CustomBlock node) {
        visitChildren(node);
        text.append(' ');
    }

    @Override
    public void visit(CustomNode node) {
        visitChildren(node);
        text.append(' ');
    }
}
