package dev.doctize.search;

import static dev.doctize.Fixtures.apiPage;
import static dev.doctize.Fixtures.guide;
import static dev.doctize.Fixtures.site;
import static dev.doctize.Fixtures.type;
import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.markdown.TextChunk;
import dev.doctize.render.RenderedPage;
import dev.doctize.site.Page;
import dev.doctize.site.PageContent;
import dev.doctize.site.Site;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchIndexBuilderTest {

    Page keywordPage = new Page("k", "Keyed", "s", "", 1, "", List.of("alpha", "beta"), new PageContent.Guide("x", ""));
    Site site = site(keywordPage, guide("plain", "Plain", "s", "y", ""));

    private RenderedPage rendered(int index, String summary, TextChunk... chunks) {
        return new RenderedPage(site.pages().get(index), "", List.of(), List.of(chunks), summary);
    }

    @Test
    void everyChunkBecomesARecordWithADeepLinkAndItsPageAndSection() {
        List<SearchRecord> records = new SearchIndexBuilder(site).build(List.of(
                rendered(0, "s", new TextChunk("", "", "Intro text."), new TextChunk("setup", "Setup", "Do this."))));

        assertThat(records).containsExactly(
                new SearchRecord("s/k.html", "Keyed", "S", "", "Intro text.", "alpha beta", "page", "guides"),
                new SearchRecord("s/k.html#setup", "Keyed", "S", "Setup", "Do this.", "", "section", "guides"));
    }

    @Test
    void keywordsAreAttachedOnlyToTheStartOfThePage() {
        List<SearchRecord> records = new SearchIndexBuilder(site).build(List.of(
                rendered(0, "s", new TextChunk("", "", "a"), new TextChunk("h", "H", "b"))));

        assertThat(records).extracting(SearchRecord::keywords).containsExactly("alpha beta", "");
    }

    @Test
    void aPageThatStartsWithAHeadingStillGetsARecordForItsTitle() {
        List<SearchRecord> records = new SearchIndexBuilder(site).build(List.of(
                rendered(0, "The summary.", new TextChunk("first", "First", "text"))));

        assertThat(records).hasSize(2);
        assertThat(records.getFirst()).isEqualTo(new SearchRecord("s/k.html", "Keyed", "S", "", "The summary.", "alpha beta", "page", "guides"));
        assertThat(records.get(1).url()).isEqualTo("s/k.html#first");
    }

    @Test
    void aPageWithoutAnyTextIsStillFindableByItsTitle() {
        List<SearchRecord> records = new SearchIndexBuilder(site).build(List.of(rendered(1, "Only a summary.")));

        assertThat(records).containsExactly(new SearchRecord("s/plain.html", "Plain", "S", "", "Only a summary.", "", "page", "guides"));
    }

    @Test
    void apiRecordsAreTaggedWithTheKindOfTypeOrMemberAndBelongToTheApiArea() {
        Site apiSite = site(apiPage(type("com.acme.Cache", "")));
        RenderedPage page = new RenderedPage(apiSite.pages().getFirst(), "", List.of(), List.of(
                new TextChunk("", "", "A cache."),
                new TextChunk("method-get", "get()", "Reads.", "method")), "");

        List<SearchRecord> records = new SearchIndexBuilder(apiSite).build(List.of(page));

        assertThat(records).extracting(SearchRecord::kind).containsExactly("class", "method");
        assertThat(records).extracting(SearchRecord::area).containsOnly(SearchRecord.API);
    }

    @Test
    void apiRecordsCarryNoDescriptionSoTheyAreFoundByNameOnly() {
        Site apiSite = site(apiPage(type("com.acme.Cache", "")));
        RenderedPage page = new RenderedPage(apiSite.pages().getFirst(), "", List.of(), List.of(
                new TextChunk("", "", "A cache."),
                new TextChunk("method-get", "get()", "Reads a value.", "method")), "A cache.");

        List<SearchRecord> records = new SearchIndexBuilder(apiSite).build(List.of(page));

        assertThat(records).extracting(SearchRecord::text).containsOnly("");
        assertThat(records).extracting(SearchRecord::heading).containsExactly("", "get()");
    }
}
