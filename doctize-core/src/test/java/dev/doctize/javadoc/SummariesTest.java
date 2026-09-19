package dev.doctize.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SummariesTest {

    @Test
    void endsAtTheFirstSentence() {
        assertThat(Summaries.firstSentence("Does a thing. And another one.")).isEqualTo("Does a thing.");
    }

    @Test
    void limitedToTheFirstParagraph() {
        assertThat(Summaries.firstSentence("No period here\nbut two lines\n\nSecond paragraph."))
                .isEqualTo("No period here but two lines");
    }

    @Test
    void ignoresPeriodsInsideTagsCodeAndAbbreviations() {
        assertThat(Summaries.firstSentence("See {@link a.b.C} for e.g. this. Next.")).isEqualTo("See {@link a.b.C} for e.g. this.");
        assertThat(Summaries.firstSentence("Use `a. b` wisely. Next.")).isEqualTo("Use `a. b` wisely.");
    }

    @Test
    void explicitSummaryTagWins() {
        assertThat(Summaries.firstSentence("Long intro. {@summary The short one} more.")).isEqualTo("The short one");
    }
}
