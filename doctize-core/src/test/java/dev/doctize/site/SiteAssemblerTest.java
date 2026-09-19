package dev.doctize.site;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.DoctizeConfig;
import dev.doctize.config.SectionSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class SiteAssemblerTest {

    private static Page page(String title, String section, int order, String group) {
        return new Page(title, title, section, group, order, "", List.of(), new PageContent.Guide("", ""));
    }

    private static Site assemble(List<SectionSpec> sections, Page... pages) {
        DoctizeConfig config = new DoctizeConfig(null, null, sections, null, null, null, null);
        return new SiteAssembler(config).assemble(List.of(pages), "");
    }

    @Test
    void declaredOrderDefinesNavigationOrderAndEmptySectionsAreOmitted() {
        Site site = assemble(
                List.of(SectionSpec.ofTitle("Quick start"), SectionSpec.ofTitle("Setup"), SectionSpec.ofTitle("Empty")),
                page("B", "Setup", 1, ""),
                page("A", "quick-start", 1, ""));

        assertThat(site.sections()).extracting(Section::title).containsExactly("Quick start", "Setup");
    }

    @Test
    void undeclaredSectionsAreCreatedOnDemandAfterDeclaredOnes() {
        Site site = assemble(List.of(SectionSpec.ofTitle("Setup")), page("X", "Recipes", 1, ""), page("Y", "Setup", 1, ""));

        assertThat(site.sections()).extracting(Section::title).containsExactly("Setup", "Recipes");
        assertThat(site.sections().get(1).id()).isEqualTo("recipes");
    }

    @Test
    void sectionsAreMatchedByIdOrTitleIgnoringCase() {
        Site site = assemble(
                List.of(new SectionSpec("api", "API Reference", "")),
                page("A", "api", 1, ""),
                page("B", "API reference", 1, ""));

        assertThat(site.sections()).hasSize(1);
        assertThat(site.sections().getFirst().pages()).hasSize(2);
    }

    @Test
    void groupsStayTogetherAndAreOrderedByTheirLowestOrder() {
        Site site = assemble(
                List.of(),
                page("A", "s", 1, "g1"),
                page("B", "s", 2, "g2"),
                page("C", "s", 3, "g1"),
                page("D", "s", 0, "g3"));

        assertThat(site.pages()).extracting(SitePage::title).containsExactly("D", "A", "C", "B");
    }

    @Test
    void pagesWithoutGroupFormOneGroup() {
        Site site = assemble(List.of(), page("Plain 2", "s", 5, ""), page("Grouped", "s", 3, "g"), page("Plain 1", "s", 1, ""));

        assertThat(site.pages()).extracting(SitePage::title).containsExactly("Plain 1", "Plain 2", "Grouped");
    }

    @Test
    void pagesAreOrderedByOrderThenGroupThenTitle() {
        Site site = assemble(
                List.of(),
                page("Zed", "s", 1, "b"),
                page("Alpha", "s", 1, "b"),
                page("Mid", "s", 1, "a"),
                page("First", "s", 0, "z"));

        assertThat(site.pages()).extracting(SitePage::title).containsExactly("First", "Mid", "Alpha", "Zed");
    }

    @Test
    void urlsAreUniqueAndKeepUrlSafeSlugs() {
        Site site = assemble(
                List.of(),
                new Page("dev.acme.Cache", "Cache", "api", "", 1, "", List.of(), new PageContent.Guide("", "")),
                page("Same", "api", 1, ""),
                page("same", "api", 2, ""),
                new Page("Weird Title!", "W", "api", "", 3, "", List.of(), new PageContent.Guide("", "")));

        assertThat(site.pages()).extracting(SitePage::url)
                .containsExactly("api/dev.acme.Cache.html", "api/Same.html", "api/same-2.html", "api/weird-title.html");
    }

    @Test
    void sectionSpecDerivesMissingIdOrTitle() {
        assertThat(new SectionSpec("", "Quick start!", "").id()).isEqualTo("quick-start");
        assertThat(new SectionSpec("how-to", "", "").title()).isEqualTo("How to");
    }
}
