package dev.doctize.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTest {

    @Test
    void escapeCoversTextAndAttributeValues() {
        assertThat(Html.escape("<a href=\"x\">Tom & Jerry</a>")).isEqualTo("&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&lt;/a&gt;");
    }

    @Test
    void escapeTextLeavesQuotesAlone() {
        assertThat(Html.escapeText("\"quoted\" & <b>")).isEqualTo("\"quoted\" &amp; &lt;b&gt;");
    }

    @Test
    void unescapeDecodesNumericAndNamedReferences() {
        assertThat(Html.unescape("a&#40;b&#x29; &lt;c&gt; &amp;amp; &quot;d&quot; &apos;e&apos;")).isEqualTo("a(b) <c> &amp; \"d\" 'e'");
    }

    @Test
    void unescapeLeavesUnknownReferencesAndPlainTextAlone() {
        assertThat(Html.unescape("no references, &unknown; & 5 < 6")).isEqualTo("no references, &unknown; & 5 < 6");
    }

    @Test
    void escapeAndUnescapeAreInverse() {
        String text = "if (a < b && c > \"d\") { 'e'; }";

        assertThat(Html.unescape(Html.escape(text))).isEqualTo(text);
    }
}
