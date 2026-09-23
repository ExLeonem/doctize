package dev.doctize.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodeMarksTest {

    @Test
    void marksBecomeElementsAndLinksGetTheirUrl() {
        String code = "a " + CodeMarks.BOLD + "b" + CodeMarks.END + " " + CodeMarks.LINK + CodeMarks.LINK_BASE + "c" + CodeMarks.END;

        assertThat(CodeMarks.toHtml(code, List.of("x.html#c"))).isEqualTo("a <b>b</b> <a href=\"x.html#c\">c</a>");
    }

    @Test
    void aMarkThatCrossesHighlightingIsSplitSoTheHtmlStaysNested() {
        String html = CodeMarks.HIGHLIGHTED + "<span class=\"tok-k\">new</span> Foo" + CodeMarks.END;

        assertThat(CodeMarks.toHtml(html, List.of()))
                .isEqualTo("<span class=\"tok-k\"><mark>new</mark></span><mark> Foo</mark>");
    }

    @Test
    void highlightedSnippetKeepsItsMarksThroughTheRenderer() {
        String markdown = "```java\nvar c = " + CodeMarks.BOLD + "Cache" + CodeMarks.END + ".of();\n```";

        String html = new MarkdownRenderer(new CodeHighlighter()).render(markdown, "", LinkRewriter.identity()).html();

        assertThat(html).contains("<b>").contains("Cache").doesNotContain(String.valueOf(CodeMarks.BOLD));
    }

    @Test
    void markedWordsKeepTheColoursOfTheHighlighting() {
        String code = "a." + CodeMarks.BOLD + "generate" + CodeMarks.END + "();";

        String html = CodeMarks.render(code, List.of(), plain -> new CodeHighlighter().highlight(plain, "java"));

        assertThat(html).contains("<span class=\"tok-f\"><b>generate</b></span>()");
    }

    @Test
    void marksCountEscapedCharactersAsOne() {
        String code = "a < " + CodeMarks.HIGHLIGHTED + "b" + CodeMarks.END;

        assertThat(CodeMarks.render(code, List.of(), dev.doctize.text.Html::escapeText)).isEqualTo("a &lt; <mark>b</mark>");
    }

    @Test
    void plainTextHasNoMarks() {
        assertThat(CodeMarks.strip("a" + CodeMarks.LINK + CodeMarks.LINK_BASE + "b" + CodeMarks.END)).isEqualTo("ab");
    }
}
