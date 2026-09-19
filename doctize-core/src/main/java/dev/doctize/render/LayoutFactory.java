package dev.doctize.render;

import dev.doctize.config.ProjectInfo;
import dev.doctize.config.ThemeSettings;
import dev.doctize.markdown.TocEntry;
import dev.doctize.render.Views.BreadcrumbView;
import dev.doctize.render.Views.HomeView;
import dev.doctize.render.Views.LayoutView;
import dev.doctize.render.Views.NavGroupView;
import dev.doctize.render.Views.NavItemView;
import dev.doctize.render.Views.NavSectionView;
import dev.doctize.render.Views.PagerLinkView;
import dev.doctize.render.Views.ProjectView;
import dev.doctize.render.Views.SectionCardView;
import dev.doctize.render.Views.ThemeView;
import dev.doctize.render.Views.TocItemView;
import dev.doctize.site.PageContent;
import dev.doctize.site.Section;
import dev.doctize.site.Site;
import dev.doctize.site.SitePage;
import dev.doctize.site.Urls;
import java.util.ArrayList;
import java.util.List;

/** Builds the data for the frame around a page: navigation, table of contents, previous/next links. */
public final class LayoutFactory {

    private static final int CARD_PREVIEW_PAGES = 4;

    private final Site site;
    private final ProjectView project;
    private final ThemeView theme;

    /** @param extraCss URLs of extra stylesheets relative to the site root; likewise {@code extraJs} */
    public LayoutFactory(Site site, ThemeSettings settings, List<String> extraCss, List<String> extraJs) {
        ProjectInfo info = site.project();
        this.site = site;
        this.project = new ProjectView(info.name(), info.tagline(), info.version(), info.repository(), info.footer());
        this.theme = new ThemeView(settings.accent(), extraCss, extraJs, settings.options());
    }

    public LayoutView forPage(RenderedPage rendered) {
        SitePage page = rendered.page();
        List<SitePage> reading = site.pages();
        int index = reading.indexOf(page);
        return new LayoutView(
                page.title() + " · " + project.name(),
                page.title(),
                rendered.summary(),
                Urls.basePath(page.url()),
                page.url(),
                false,
                rendered.html(),
                project,
                theme,
                navigation(page),
                rendered.toc().stream().map(LayoutFactory::tocItem).toList(),
                breadcrumb(page),
                index > 0 ? pagerLink(reading.get(index - 1)) : null,
                index < reading.size() - 1 ? pagerLink(reading.get(index + 1)) : null);
    }

    public LayoutView forHome(String contentHtml) {
        return new LayoutView(
                project.name(),
                project.name(),
                project.tagline(),
                "",
                "index.html",
                true,
                contentHtml,
                project,
                theme,
                navigation(null),
                List.of(),
                null,
                null,
                null);
    }

    public HomeView homeView(String introHtml) {
        List<SectionCardView> cards = site.sections().stream().map(LayoutFactory::card).toList();
        String startUrl = cards.isEmpty() ? "" : cards.getFirst().url();
        return new HomeView(introHtml, cards, project, startUrl);
    }

    private List<NavSectionView> navigation(SitePage current) {
        return site.sections().stream().map(section -> navSection(section, current)).toList();
    }

    private static NavSectionView navSection(Section section, SitePage current) {
        List<NavGroupView> groups = new ArrayList<>();
        String label = null;
        List<NavItemView> items = new ArrayList<>();
        for (SitePage page : section.pages()) {
            String group = page.page().group();
            if (!group.equals(label) && !items.isEmpty()) {
                groups.add(new NavGroupView(label, List.copyOf(items)));
                items.clear();
            }
            label = group;
            items.add(navItem(page, current));
        }
        if (!items.isEmpty()) {
            groups.add(new NavGroupView(label, List.copyOf(items)));
        }
        boolean isCurrent = current != null && current.section().equals(section.id());
        return new NavSectionView(section.id(), section.title(), section.pages().getFirst().url(), isCurrent, groups);
    }

    private static NavItemView navItem(SitePage page, SitePage current) {
        boolean deprecated = page.page().content() instanceof PageContent.Type type
                && type.element().declaration().isDeprecated();
        return new NavItemView(page.title(), page.url(), page.equals(current), deprecated);
    }

    private static SectionCardView card(Section section) {
        List<NavItemView> preview = section.pages().stream()
                .limit(CARD_PREVIEW_PAGES)
                .map(page -> navItem(page, null))
                .toList();
        int count = section.pages().size();
        String label = count == 1 ? "1 page" : count + " pages";
        return new SectionCardView(section.title(), section.description(), section.pages().getFirst().url(), label, preview);
    }

    private BreadcrumbView breadcrumb(SitePage page) {
        return site.sections().stream()
                .filter(section -> section.id().equals(page.section()))
                .findFirst()
                .map(section -> new BreadcrumbView(section.title(), section.pages().getFirst().url()))
                .orElse(null);
    }

    private PagerLinkView pagerLink(SitePage page) {
        String section = site.sections().stream()
                .filter(candidate -> candidate.id().equals(page.section()))
                .map(Section::title)
                .findFirst()
                .orElse("");
        return new PagerLinkView(page.title(), section, page.url());
    }

    private static TocItemView tocItem(TocEntry entry) {
        return new TocItemView(entry.text(), entry.id(), entry.level() > 2);
    }
}
