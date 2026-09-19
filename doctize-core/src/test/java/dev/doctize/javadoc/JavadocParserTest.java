package dev.doctize.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JavadocParserTest {

    @Test
    void splitsDescriptionAndBlockTags() {
        JavadocComment comment = JavadocParser.parse("""
                 Does a thing.

                 More detail.
                 @param key the key
                 @return the value
                 @since 1.2
                """);

        assertThat(comment.description()).isEqualTo("Does a thing.\n\nMore detail.");
        assertThat(comment.tags()).extracting(BlockTag::name).containsExactly("param", "return", "since");
        assertThat(comment.paramTag("key")).get().extracting(BlockTag::text).isEqualTo("the key");
    }

    @Test
    void tagTextMayContinueOnFollowingLines() {
        JavadocComment comment = JavadocParser.parse("@throws IOException if reading\n  fails badly\n@since 2");

        assertThat(comment.throwsTag("java.io.IOException")).get()
                .extracting(BlockTag::text)
                .isEqualTo("if reading\nfails badly");
    }

    @Test
    void atSignsInFencedCodeAreNotTags() {
        JavadocComment comment = JavadocParser.parse("""
                Example:

                ```java
                @Override
                void run() {}
                ```
                @since 1
                """);

        assertThat(comment.description()).contains("@Override");
        assertThat(comment.tags()).extracting(BlockTag::name).containsExactly("since");
    }

    @Test
    void atSignsInsidePreAreNotTags() {
        JavadocComment comment = JavadocParser.parse("<pre>{@code\n@Deprecated\nclass A {}\n}</pre>\n@see Other");

        assertThat(comment.description()).contains("@Deprecated");
        assertThat(comment.tags()).extracting(BlockTag::name).containsExactly("see");
    }

    @Test
    void keepsUnknownTagsAndHandlesEmptyInput() {
        assertThat(JavadocParser.parse("@apiNote careful").tags()).extracting(BlockTag::name).containsExactly("apiNote");
        assertThat(JavadocParser.parse(null).isEmpty()).isTrue();
        assertThat(JavadocParser.parse("  ").isEmpty()).isTrue();
    }

    @Test
    void removesCommonIndentationEvenWhenTheCommentEndsWithANewline() {
        assertThat(JavadocParser.parse(" First line\n <p>Second\n").description()).isEqualTo("First line\n<p>Second");
    }
}
