package dev.doctize.output;

import dev.doctize.render.LayoutFactory;
import dev.doctize.render.PageRenderer;
import dev.doctize.render.RenderedPage;
import dev.doctize.search.SearchIndexBuilder;
import dev.doctize.search.SearchIndexWriter;
import dev.doctize.site.Site;
import dev.doctize.theme.TemplateEngine;
import java.util.List;

/** Renders every page of a site inside the layout and writes the HTML and the search index. */
public final class SiteWriter {

    private final Site site;
    private final PageRenderer pages;
    private final LayoutFactory layouts;
    private final TemplateEngine templates;
    private final OutputDirectory output;

    public SiteWriter(
            Site site, PageRenderer pages, LayoutFactory layouts, TemplateEngine templates, OutputDirectory output) {
        this.site = site;
        this.pages = pages;
        this.layouts = layouts;
        this.templates = templates;
        this.output = output;
    }

    /** @return the number of pages written, not counting the home page */
    public int write() {
        List<RenderedPage> rendered = site.pages().stream().map(pages::render).toList();
        for (RenderedPage page : rendered) {
            output.write(page.page().url(), templates.render("layout", layouts.forPage(page)));
        }
        writeHome();
        output.write(
                SearchIndexWriter.FILE_NAME,
                SearchIndexWriter.toScript(new SearchIndexBuilder(site).build(rendered)));
        return rendered.size();
    }

    private void writeHome() {
        String home = templates.render("home", layouts.homeView(pages.renderIntro(site.intro())));
        output.write("index.html", templates.render("layout", layouts.forHome(home)));
    }
}
