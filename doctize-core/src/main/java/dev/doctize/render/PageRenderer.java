package dev.doctize.render;

import dev.doctize.javadoc.InlineTagProcessor;
import dev.doctize.javadoc.JavadocParser;
import dev.doctize.javadoc.LinkResolver;
import dev.doctize.javadoc.SnippetSource;
import dev.doctize.javadoc.Summaries;
import dev.doctize.markdown.CodeHighlighter;
import dev.doctize.markdown.MarkdownRenderer;
import dev.doctize.markdown.RenderedMarkdown;
import dev.doctize.markdown.TextChunk;
import dev.doctize.markdown.TocEntry;
import dev.doctize.model.SourceElement;
import dev.doctize.render.Views.GuideView;
import dev.doctize.site.Page;
import dev.doctize.site.PageContent;
import dev.doctize.site.Site;
import dev.doctize.site.SitePage;
import dev.doctize.site.Urls;
import dev.doctize.theme.TemplateEngine;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Renders the content of a page: guides through the Markdown pipeline, types through their reference template. */
public final class PageRenderer {

    private static final int SUMMARY_LENGTH = 180;
    private static final SitePage HOME = new SitePage(
            new Page("index", "Home", "", "", 0, "", List.of(), new PageContent.Guide("", "index.md")), "", "index.html");

    private final MarkdownRenderer markdown;
    private final CodeHighlighter highlighter;
    private final TemplateEngine templates;
    private final TypeIndex types;
    private final Map<String, SitePage> pagesBySource = new HashMap<>();
    private final Map<String, SitePage> pagesByUrl = new HashMap<>();
    private final Set<String> warnings = new LinkedHashSet<>();

    public PageRenderer(Site site, CodeHighlighter highlighter, TemplateEngine templates) {
        this.markdown = new MarkdownRenderer(highlighter);
        this.highlighter = highlighter;
        this.templates = templates;
        this.types = new TypeIndex(site);
        for (SitePage page : site.pages()) {
            pagesByUrl.put(page.url(), page);
            if (page.page().content() instanceof PageContent.Guide guide && !guide.sourcePath().isEmpty()) {
                pagesBySource.put(guide.sourcePath(), page);
            }
        }
    }

    public RenderedPage render(SitePage page) {
        return switch (page.page().content()) {
            case PageContent.Guide guide -> renderGuide(page, guide);
            case PageContent.Type type -> renderType(page, type);
        };
    }

    /** Problems found while rendering, e.g. link tags that point nowhere. Complete once all pages are rendered. */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    /** Renders the Markdown introduction of the home page. */
    public String renderIntro(String markdownText) {
        return markdownText.isBlank() ? "" : fragmentsFor(HOME, "index.md", SnippetSource.none()).html(markdownText, "");
    }

    private RenderedPage renderGuide(SitePage page, PageContent.Guide guide) {
        Fragments fragments = fragmentsFor(page, guide.sourcePath(), SnippetSource.of(guide.snippets()));
        RenderedMarkdown body = fragments.render(guide.markdown(), "");
        String description = page.page().description();
        GuideView view = new GuideView(page.title(), fragments.inlineHtml(description), body.html());
        List<TextChunk> chunks = body.chunks().isEmpty() ? List.of(new TextChunk("", "", description)) : body.chunks();
        String summary = description.isBlank() ? firstText(chunks) : description;
        return new RenderedPage(page, templates.render("guide", view), pageLevelToc(body.toc()), chunks, summary);
    }

    private RenderedPage renderType(SitePage page, PageContent.Type type) {
        Fragments fragments = fragmentsFor(page, "", SnippetSource.of(snippetsOf(type.element())));
        Function<String, Optional<String>> typeLinks =
                name -> types.typeUrl(name).map(url -> Urls.relative(page.url(), url));
        TypeDocs docs = new ApiTypeViewFactory(fragments, highlighter, typeLinks).create(type.element(), page.title());
        String summary = truncate(fragments.plain(Summaries.firstSentence(
                JavadocParser.parse(type.element().comment()).description())));
        return new RenderedPage(page, templates.render("api-type", docs.view()), docs.toc(), docs.chunks(), summary);
    }

    private Fragments fragmentsFor(SitePage page, String sourcePath, SnippetSource snippets) {
        LinkResolver resolver = reference -> types.resolve(reference, page).map(url -> Urls.relative(page.url(), url));
        return new Fragments(
                markdown,
                new InlineTagProcessor(
                        resolver,
                        reference -> reportUnresolved(page, reference),
                        snippets,
                        problem -> warnings.add(page.url() + ": " + problem)),
                new GuideLinkRewriter(page.url(), sourcePath, pagesBySource, pagesByUrl));
    }

    private void reportUnresolved(SitePage page, String reference) {
        if (!JdkTypes.isKnown(reference)) {
            warnings.add(page.url() + ": {@link " + reference + "} does not refer to a documented type or member");
        }
    }

    /** The snippet files of a type and all its members; a member's comment may show a file just like the type's. */
    private static Map<String, String> snippetsOf(SourceElement element) {
        Map<String, String> files = new HashMap<>(element.snippets());
        element.members().forEach(member -> files.putAll(snippetsOf(member)));
        return files;
    }

    /** Only second and third level headings appear in the "on this page" navigation. */
    private static List<TocEntry> pageLevelToc(List<TocEntry> headings) {
        return headings.stream().filter(entry -> entry.level() == 2 || entry.level() == 3).toList();
    }

    private static String firstText(List<TextChunk> chunks) {
        return chunks.stream()
                .map(TextChunk::text)
                .filter(text -> !text.isBlank())
                .findFirst()
                .map(PageRenderer::truncate)
                .orElse("");
    }

    private static String truncate(String text) {
        if (text.length() <= SUMMARY_LENGTH) {
            return text;
        }
        int cut = text.lastIndexOf(' ', SUMMARY_LENGTH);
        return text.substring(0, cut > 0 ? cut : SUMMARY_LENGTH) + "…";
    }
}
