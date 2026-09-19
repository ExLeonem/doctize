package dev.doctize.render;

import static dev.doctize.Fixtures.apiPage;
import static dev.doctize.Fixtures.guide;
import static dev.doctize.Fixtures.method;
import static dev.doctize.Fixtures.siteWithSections;
import static dev.doctize.Fixtures.type;
import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.SectionSpec;
import dev.doctize.config.ThemeSettings;
import dev.doctize.hosting.BaseUrl;
import dev.doctize.markdown.TocEntry;
import dev.doctize.render.Views.LayoutView;
import dev.doctize.render.Views.NavSectionView;
import dev.doctize.site.Page;
import dev.doctize.site.PageContent;
import dev.doctize.site.Site;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LayoutFactoryTest {

    Page deprecatedType = new Page("a.Old", "Old", "api", "a", 1, "", List.of(), new PageContent.Type(withDeprecation()));
    Site site = siteWithSections(
            List.of(SectionSpec.ofTitle("Start"), SectionSpec.ofTitle("api")),
            guide("one", "One", "start", "x", ""), guide("two", "Two", "start", "y", ""), apiPage(type("a.Fresh", "F.", method("m", "", "void"))), deprecatedType);

    private static dev.doctize.model.SourceElement withDeprecation() {
        dev.doctize.model.SourceElement plain = type("a.Old", "Old.");
        dev.doctize.model.Declaration d = plain.declaration();
        return new dev.doctize.model.SourceElement(plain.role(),
                new dev.doctize.model.Declaration(d.kind(), d.name(), d.qualifiedName(), d.packageName(), d.signature(), List.of("@Deprecated"), d.parameters(), d.returnType(), d.thrownTypes()),
                plain.comment(), plain.placement(), plain.keywords(), plain.members());
    }

    LayoutFactory factory(Optional<BaseUrl> base) {
        return new LayoutFactory(site, new ThemeSettings("", "#abc", List.of("c.css"), List.of("j.js"), Map.of("k", "v")), List.of("assets/custom/c.css"), List.of("assets/custom/j.js"), base);
    }

    private RenderedPage rendered(int index) {
        return new RenderedPage(site.pages().get(index), "<p>body</p>", List.of(new TocEntry(2, "H", "h"), new TocEntry(3, "S", "s")), List.of(), "Summary.");
    }

    @Test
    void aPageKnowsItsPositionInTheReadingOrder() {
        LayoutView first = factory(Optional.empty()).forPage(rendered(0));
        LayoutView middle = factory(Optional.empty()).forPage(rendered(1));
        LayoutView last = factory(Optional.empty()).forPage(rendered(3));

        assertThat(first.previous()).isNull();
        assertThat(first.next().title()).isEqualTo("Two");
        assertThat(first.next().section()).isEqualTo("Start");
        assertThat(middle.previous().title()).isEqualTo("One");
        assertThat(last.next()).isNull();
    }

    @Test
    void theFrameCarriesTitlePathsContentAndTheTableOfContents() {
        LayoutView view = factory(Optional.empty()).forPage(rendered(2));

        assertThat(view.documentTitle()).isEqualTo("Fresh · Documentation");
        assertThat(view.title()).isEqualTo("Fresh");
        assertThat(view.description()).isEqualTo("Summary.");
        assertThat(view.basePath()).isEqualTo("../");
        assertThat(view.url()).isEqualTo("api/a.Fresh.html");
        assertThat(view.home()).isFalse();
        assertThat(view.contentHtml()).isEqualTo("<p>body</p>");
        assertThat(view.toc()).extracting(Views.TocItemView::text).containsExactly("H", "S");
        assertThat(view.toc()).extracting(Views.TocItemView::nested).containsExactly(false, true);
        assertThat(view.section().title()).isEqualTo("api");
        assertThat(view.theme().accent()).isEqualTo("#abc");
        assertThat(view.theme().extraCss()).containsExactly("assets/custom/c.css");
        assertThat(view.theme().options()).containsEntry("k", "v");
    }

    @Test
    void navigationMarksTheCurrentSectionAndPageAndStrikesDeprecatedTypes() {
        LayoutView view = factory(Optional.empty()).forPage(rendered(1));

        assertThat(view.navigation()).extracting(NavSectionView::current).containsExactly(true, false);
        var items = view.navigation().getFirst().groups().getFirst().items();
        assertThat(items).extracting(Views.NavItemView::current).containsExactly(false, true);
        var apiItems = view.navigation().get(1).groups().stream().flatMap(g -> g.items().stream()).toList();
        assertThat(apiItems).filteredOn(Views.NavItemView::deprecated).extracting(Views.NavItemView::title).containsExactly("Old");
    }

    @Test
    void groupsAreLabelledByTheirPackage() {
        NavSectionView api = factory(Optional.empty()).forPage(rendered(0)).navigation().get(1);

        assertThat(api.groups()).singleElement().satisfies(group -> {
            assertThat(group.label()).isEqualTo("a");
            assertThat(group.items()).hasSize(2);
        });
    }

    @Test
    void theHomePageHasNoCurrentSectionAndListsCardsWithPageCounts() {
        LayoutFactory factory = factory(Optional.empty());

        LayoutView home = factory.forHome("<p>intro</p>");
        Views.HomeView view = factory.homeView("<p>intro</p>");

        assertThat(home.home()).isTrue();
        assertThat(home.basePath()).isEmpty();
        assertThat(home.navigation()).noneMatch(NavSectionView::current);
        assertThat(view.sections()).extracting(Views.SectionCardView::pageLabel).containsExactly("2 pages", "2 pages");
        assertThat(view.startUrl()).isEqualTo("start/one.html");
        assertThat(view.introHtml()).isEqualTo("<p>intro</p>");
    }

    @Test
    void aSingularPageCountReadsNaturally() {
        Site single = siteWithSections(List.of(), guide("only", "Only", "s", "x", ""));

        Views.HomeView view = new LayoutFactory(single, ThemeSettings.defaults(), List.of(), List.of(), Optional.empty()).homeView("");

        assertThat(view.sections().getFirst().pageLabel()).isEqualTo("1 page");
    }

    @Test
    void canonicalUrlsExistOnlyWhenTheAddressIsKnown() {
        assertThat(factory(Optional.empty()).forPage(rendered(0)).canonicalUrl()).isEmpty();
        assertThat(factory(Optional.empty()).forHome("").canonicalUrl()).isEmpty();

        LayoutFactory known = factory(Optional.of(BaseUrl.of("https://ex.org/docs")));

        assertThat(known.forPage(rendered(0)).canonicalUrl()).isEqualTo("https://ex.org/docs/start/one.html");
        assertThat(known.forHome("").canonicalUrl()).isEqualTo("https://ex.org/docs/");
    }

    @Test
    void theNotFoundFrameUsesAbsolutePathsAndNoCanonicalUrl() {
        LayoutFactory factory = factory(Optional.of(BaseUrl.of("https://ex.org/docs")));
        BaseUrl base = BaseUrl.of("https://ex.org/docs");

        LayoutView view = factory.forNotFound(base, "<p>gone</p>");

        assertThat(view.basePath()).isEqualTo("/docs/");
        assertThat(view.canonicalUrl()).isEmpty();
        assertThat(view.title()).isEqualTo("Page not found");
        assertThat(view.toc()).isEmpty();
        assertThat(factory.notFoundView(base).basePath()).isEqualTo("/docs/");
    }
}
