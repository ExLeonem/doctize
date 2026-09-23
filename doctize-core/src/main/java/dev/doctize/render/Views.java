package dev.doctize.render;

import dev.doctize.annotations.Doc;
import java.util.List;
import java.util.Map;

/**
 * The data the theme's templates can use. Templates are logic-less, so these records carry
 * everything ready to print: HTML fields ({@code ...Html}) are inserted unescaped with
 * {@code {{{triple braces}}}}, all other strings with {@code {{double braces}}}.
 *
 * <p>URL fields are relative to the site root; templates prefix them with {@code {{basePath}}}.
 */
@Doc(group = "Theming", order = 51)
public final class Views {

    private Views() {}

    public record ProjectView(String name, String tagline, String version, String repository, String footer) {}

    public record ThemeView(String name, List<String> stylesheets, String accent, List<String> extraCss, List<String> extraJs, Map<String, String> options) {}

    /** A page in the navigation; {@code headings} lists the top-level headings of the current page, and is empty for all others. */
    public record NavItemView(String title, String url, boolean current, boolean deprecated, List<TocItemView> headings) {}

    public record NavGroupView(String label, List<NavItemView> items) {}

    /** A section in the navigation. {@code area} is {@code "api"} for a section of API pages, else {@code "guides"}. */
    public record NavSectionView(String id, String title, String url, boolean current, List<NavGroupView> groups, String area) {}

    /** A tab above the navigation that shows the sections of one {@code area}; {@code current} if the page is there. */
    public record NavTabView(String area, String title, boolean current) {}

    public record TocItemView(String text, String id, boolean nested) {}

    public record PagerLinkView(String title, String section, String url) {}

    public record BreadcrumbView(String title, String url) {}

    /**
     * Everything the frame around a page needs. {@code contentHtml} is the already rendered page body.
     * {@code navTabs} is empty unless the site has both guides and API pages.
     */
    public record LayoutView(
            String documentTitle,
            String title,
            String description,
            String basePath,
            String url,
            boolean home,
            String contentHtml,
            ProjectView project,
            ThemeView theme,
            List<NavSectionView> navigation,
            List<TocItemView> toc,
            BreadcrumbView section,
            PagerLinkView previous,
            PagerLinkView next,
            String canonicalUrl,
            List<NavTabView> navTabs) {}

    public record SectionCardView(String title, String description, String url, String pageLabel, List<NavItemView> pages) {}

    /** The context of {@code not-found.mustache}. {@code basePath} is absolute, since the page is shown at any depth. */
    public record NotFoundView(String basePath, ProjectView project) {}

    public record HomeView(String introHtml, List<SectionCardView> sections, ProjectView project, String startUrl) {}

    public record GuideView(String title, String descriptionHtml, String bodyHtml) {}

    public record TagView(String label, String html) {}

    public record DeprecationView(String html) {}

    public record ParamView(String name, String typeHtml, String descriptionHtml) {}

    public record ReturnView(String typeHtml, String descriptionHtml) {}

    public record ThrowsView(String typeHtml, String descriptionHtml) {}

    public record MemberView(
            String id,
            String name,
            String kind,
            String signatureHtml,
            String summaryHtml,
            String descriptionHtml,
            DeprecationView deprecated,
            List<ParamView> params,
            ReturnView returns,
            List<ThrowsView> throwsList,
            List<TagView> tags) {}

    public record MemberGroupView(String id, String title, List<MemberView> members) {}

    public record TypeView(
            String title,
            String name,
            String qualifiedName,
            String packageName,
            String kind,
            String signatureHtml,
            String descriptionHtml,
            DeprecationView deprecated,
            List<ParamView> params,
            List<TagView> tags,
            List<MemberGroupView> groups) {}
}
