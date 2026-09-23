package dev.doctize.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.markdown.CodeMarks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InlineTagProcessorTest {

    private final Map<String, String> targets =
            Map.of("Cache", "api/Cache.html", "Cache#get(Object)", "api/Cache.html#get");
    private final InlineTagProcessor processor = new InlineTagProcessor(ref -> Optional.ofNullable(targets.get(ref)));

    @Test
    void codeBecomesEscapedInlineCode() {
        assertThat(processor.process("Use {@code a<b> * c_d}.")).isEqualTo("Use <code>a&#60;b&#62; &#42; c&#95;d</code>.");
    }

    @Test
    void codeMayContainBalancedBraces() {
        assertThat(processor.process("{@code Map<K,V> m = new HashMap<>() {}}")).contains("&#123;&#125;</code>");
    }

    @Test
    void resolvedLinksBecomeAnchorsWithCodeLabels() {
        assertThat(processor.process("See {@link Cache}."))
                .isEqualTo("See <a href=\"api/Cache.html\"><code>Cache</code></a>.");
    }

    @Test
    void linkLabelsAndParenthesizedReferences() {
        assertThat(processor.process("{@link Cache#get(Object) the getter}"))
                .isEqualTo("<a href=\"api/Cache.html#get\"><code>the getter</code></a>");
        assertThat(processor.process("{@linkplain Cache caches}")).isEqualTo("<a href=\"api/Cache.html\">caches</a>");
    }

    @Test
    void unresolvedLinksDegradeToPlainCode() {
        assertThat(processor.process("{@link java.util.List}")).isEqualTo("<code>java&#46;util&#46;List</code>");
        assertThat(processor.process("{@link #size}")).isEqualTo("<code>size</code>");
    }

    @Test
    void literalEscapesMarkup() {
        assertThat(processor.process("{@literal <b>}")).isEqualTo("&#60;b&#62;");
    }

    @Test
    void codeSpansAndFencedBlocksAreLeftAlone() {
        String text = "Keep `{@code x}` as is.\n\n```java\n{@link Cache}\n```\n\nBut {@code y}.";

        assertThat(processor.process(text)).contains("`{@code x}`").contains("{@link Cache}").contains("<code>y</code>");
    }

    @Test
    void malformedTagsAreKept() {
        assertThat(processor.process("An open {@code brace")).isEqualTo("An open {@code brace");
        assertThat(processor.process("{@} and { @code}")).isEqualTo("{@} and { @code}");
    }

    @Test
    void preCodeBlocksBecomeFencedCode() {
        String result = processor.process("Intro\n<pre>{@code\nCache<K> c;\n    c.get();\n}</pre>\nOutro");

        assertThat(result).contains("```java\nCache<K> c;\n    c.get();\n```");
    }

    @Test
    void referenceLinksResolveOnlyKnownTargets() {
        assertThat(processor.process("[Cache] and [the cache][Cache]"))
                .isEqualTo("<a href=\"api/Cache.html\"><code>Cache</code></a> and <a href=\"api/Cache.html\">the cache</a>");
        assertThat(processor.process("[unknown] [link](http://x) - [ ] todo"))
                .isEqualTo("[unknown] [link](http://x) - [ ] todo");
    }

    @Test
    void snippetWithInlineContentBecomesFencedCode() {
        assertThat(processor.process("{@snippet lang=kotlin :\nval x = 1\n}")).contains("```kotlin\nval x = 1\n```");
    }

    private final Map<String, String> snippetFiles = Map.of("file:Example.java", """
            class Example {
                void run() {
                    // @start region=setup
                    Cache cache = Cache.builder().build(); // @highlight substring="builder"
                    // @end
                    cache.put("k", "v");
                }
            }
            """);
    private final List<String> problems = new ArrayList<>();
    private final List<String> unresolved = new ArrayList<>();
    private final InlineTagProcessor withSnippets = new InlineTagProcessor(
            ref -> Optional.ofNullable(targets.get(ref)), unresolved::add, SnippetSource.of(snippetFiles), problems::add);

    @Test
    void snippetContentMayFollowTheColonOnTheSameLine() {
        assertThat(processor.process("{@snippet lang=java : int x = 1;}")).contains("```java\nint x = 1;\n```");
    }

    @Test
    void externalSnippetShowsTheWholeFileInTheLanguageOfItsExtension() {
        String markdown = withSnippets.process("{@snippet file=\"Example.java\"}");

        assertThat(markdown).contains("```java\nclass Example {").contains("cache.put(\"k\", \"v\");");
        assertThat(markdown).doesNotContain("@start").doesNotContain("@end").doesNotContain("@highlight");
    }

    @Test
    void aRegionOfAnExternalSnippetShowsOnlyItsLinesWithoutIndentation() {
        String markdown = withSnippets.process("{@snippet file=Example.java region=setup}");

        assertThat(CodeMarks.strip(markdown)).contains("```java\nCache cache = Cache.builder().build();\n```");
    }

    @Test
    void aMissingFileOrRegionIsReportedAndShowsNothing() {
        assertThat(withSnippets.process("{@snippet class=com.acme.Missing}")).isEmpty();
        assertThat(withSnippets.process("{@snippet file=Example.java region=nowhere}")).isEmpty();

        assertThat(problems).hasSize(2);
        assertThat(problems.getFirst()).contains("class=com.acme.Missing").contains("not found");
        assertThat(problems.get(1)).contains("no region \"nowhere\"");
    }

    @Test
    void highlightMarksTheSubstringAndRemovesTheMarkupComment() {
        String markdown = processor.process("""
                {@snippet :
                var cache = Cache.builder(); // @highlight substring="builder" type=highlighted
                }""");

        assertThat(markdown).contains("var cache = Cache." + CodeMarks.HIGHLIGHTED + "builder" + CodeMarks.END + "();\n```");
    }

    @Test
    void markupEndingInAColonAppliesToTheNextLineAndARegionToAllLinesUpToItsEnd() {
        String markdown = processor.process("""
                {@snippet :
                // @highlight:
                a();
                b(); // @highlight region
                c();
                // @end
                d();
                }""");

        assertThat(markdown).contains(CodeMarks.BOLD + "a();" + CodeMarks.END + "\n"
                + CodeMarks.BOLD + "b();" + CodeMarks.END + "\n"
                + CodeMarks.BOLD + "c();" + CodeMarks.END + "\nd();");
    }

    @Test
    void replaceChangesTheLineBeforeItIsShown() {
        assertThat(processor.process("""
                {@snippet :
                String secret = "hunter2"; // @replace regex='".*"' replacement='"..."'
                }""")).contains("String secret = \"...\";");
    }

    @Test
    void linkMarkupLinksTheSubstringAndPassesTheUrlToTheFence() {
        String markdown = withSnippets.process("""
                {@snippet :
                Cache cache = lookup(); // @link substring="Cache" target="Cache"
                x.run(); // @link substring="run" target="Nowhere#run"
                }""");

        assertThat(markdown).contains("```java links=\"api/Cache.html\"");
        assertThat(markdown).contains(CodeMarks.LINK + "" + CodeMarks.LINK_BASE + "Cache" + CodeMarks.END + " cache");
        assertThat(unresolved).containsExactly("Nowhere#run");
    }
}
