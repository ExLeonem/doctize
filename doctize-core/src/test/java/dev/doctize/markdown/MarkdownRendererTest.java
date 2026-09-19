package dev.doctize.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer(new CodeHighlighter());

    @Test
    void headingsGetUniqueIdsAndPermalinks() {
        RenderedMarkdown result = renderer.render("## Setup\n\ntext\n\n## Setup\n\n### Details & more");

        assertThat(result.toc()).extracting(TocEntry::id).containsExactly("setup", "setup-2", "details-more");
        assertThat(result.html()).contains("<h2 id=\"setup\">Setup<a ").contains("class=\"anchor\"").contains("href=\"#setup-2\"");
    }

    @Test
    void idPrefixKeepsFragmentsApart() {
        String id = renderer.render("## Notes", "m1-", LinkRewriter.identity()).toc().getFirst().id();

        assertThat(id).isEqualTo("m1-notes");
    }

    @Test
    void codeBlocksAreHighlightedAndTitled() {
        String html = renderer.render("```java title=\"A.java\"\nclass A {} // hi\n```").html();

        assertThat(html).contains("<div class=\"code-title\">A.java</div>")
                .contains("<span class=\"tok-k\">class</span>")
                .contains("<span class=\"tok-c\">// hi</span>");
    }

    @Test
    void consecutiveTabbedBlocksShareOneContainer() {
        String html = renderer.render("```groovy tab=\"Gradle\"\na\n```\n\n```xml tab=\"Maven\"\nb\n```\n\ntext").html();

        assertThat(html.split("class=\"tabs\"", -1)).hasSize(2);
        assertThat(html).contains("data-tab=\"Gradle\"").contains("data-tab=\"Maven\"");
    }

    @Test
    void linksAndImagesAreRewrittenAndExternalLinksMarked() {
        LinkRewriter rewriter = new LinkRewriter() {
            @Override
            public String link(String destination) {
                return destination.startsWith("http") ? destination : "L/" + destination;
            }

            @Override
            public String image(String destination) {
                return "I/" + destination;
            }
        };

        String html = renderer.render("[a](x.md) [b](https://e.org) ![i](p.png)", "", rewriter).html();

        assertThat(html).contains("href=\"L/x.md\"")
                .contains("href=\"https://e.org\"")
                .contains("class=\"external\"")
                .contains("src=\"I/p.png\"");
    }

    @Test
    void gfmFeaturesAreSupported() {
        String html = renderer
                .render("| a | b |\n|---|---|\n| 1 | 2 |\n\n~~gone~~\n\n> [!WARNING]\n> careful\n\n- [x] done")
                .html();

        assertThat(html).contains("<table>")
                .contains("<del>gone</del>")
                .contains("markdown-alert-warning")
                .contains("type=\"checkbox\"");
    }

    @Test
    void chunksSplitTextAtHeadings() {
        RenderedMarkdown result = renderer.render("Intro text.\n\n## First\n\nAlpha `code`.\n\n## Second\n\nBeta.");

        assertThat(result.chunks()).extracting(TextChunk::heading).containsExactly("", "First", "Second");
        assertThat(result.chunks().get(1).text()).isEqualTo("Alpha code.");
    }

    @Test
    void titledMarkdownSeparatesTheLeadingHeading() {
        TitledMarkdown titled = TitledMarkdown.split("\n# My Title\n\nBody\n\n# Later");

        assertThat(titled.title()).contains("My Title");
        assertThat(titled.body()).isEqualTo("Body\n\n# Later");
        assertThat(TitledMarkdown.split("## Not a title\ntext").title()).isEmpty();
    }

    @Test
    void fenceInfoParsesLanguageAndAttributes() {
        FenceInfo info = FenceInfo.parse("Kotlin title=\"Build file\" tab=Gradle");

        assertThat(info.language()).isEqualTo("kotlin");
        assertThat(info.title()).isEqualTo("Build file");
        assertThat(info.tab()).isEqualTo("Gradle");
        assertThat(FenceInfo.parse(null).language()).isEmpty();
    }
}
