package dev.doctize.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CodeHighlighterTest {

    private final CodeHighlighter highlighter = new CodeHighlighter();

    @Test
    void highlightsJavaTokens() {
        String html = highlighter.highlight("@Doc(order = 1)\nString s = \"a<b\"; // c", "java");

        assertThat(html).contains("<span class=\"tok-a\">@Doc</span>")
                .contains("<span class=\"tok-n\">1</span>")
                .contains("<span class=\"tok-t\">String</span>")
                .contains("<span class=\"tok-s\">\"a&lt;b\"</span>")
                .contains("<span class=\"tok-c\">// c</span>");
    }

    @Test
    void unknownLanguagesAreOnlyEscaped() {
        assertThat(highlighter.highlight("a < b && c", "brainfuck")).isEqualTo("a &lt; b &amp;&amp; c");
        assertThat(highlighter.highlight("<x>", "")).isEqualTo("&lt;x&gt;");
    }

    @Test
    void highlightsConfigurationFormats() {
        assertThat(highlighter.highlight("name: demo # note", "yaml"))
                .contains("<span class=\"tok-p\">name</span>")
                .contains("<span class=\"tok-c\"># note</span>");
        assertThat(highlighter.highlight("{\"a\": true}", "json"))
                .contains("<span class=\"tok-p\">\"a\"</span>")
                .contains("<span class=\"tok-k\">true</span>");
        assertThat(highlighter.highlight("<a href=\"x\">", "xml")).contains("<span class=\"tok-f\">href</span>");
    }

    @Test
    void typeNamesCanBeLinked() {
        String html = highlighter.highlight(
                "Cache<String> c", "java", name -> name.equals("Cache") ? Optional.of("api/Cache.html") : Optional.empty());

        assertThat(html)
                .contains("<a class=\"tok-t\" href=\"api/Cache.html\">Cache</a>")
                .contains("<span class=\"tok-t\">String</span>");
    }
}
