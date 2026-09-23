package dev.doctize.search;

import dev.doctize.markdown.TextChunk;
import dev.doctize.render.RenderedPage;
import dev.doctize.site.PageContent;
import dev.doctize.site.Site;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the searchable text of all rendered pages, one record per heading. API pages are found by the names of
 * types and members and by their keywords only: their descriptions are neither searched nor shown in results.
 */
public final class SearchIndexBuilder {

    private final Site site;

    public SearchIndexBuilder(Site site) {
        this.site = site;
    }

    public List<SearchRecord> build(List<RenderedPage> pages) {
        List<SearchRecord> records = new ArrayList<>();
        for (RenderedPage rendered : pages) {
            String section = sectionTitle(rendered.page().section());
            String keywords = String.join(" ", rendered.page().page().keywords());
            PageContent content = rendered.page().page().content();
            String area = content instanceof PageContent.Type ? SearchRecord.API : SearchRecord.GUIDES;
            boolean api = area.equals(SearchRecord.API);
            String pageKind = content instanceof PageContent.Type type ? type.element().declaration().kind().label() : "page";
            boolean startsWithHeading = !rendered.chunks().isEmpty() && !rendered.chunks().getFirst().heading().isEmpty();
            if (startsWithHeading || rendered.chunks().isEmpty()) {
                records.add(new SearchRecord(
                        rendered.page().url(), rendered.page().title(), section, "", api ? "" : rendered.summary(), keywords, pageKind, area));
            }
            for (TextChunk chunk : rendered.chunks()) {
                boolean pageStart = chunk.heading().isEmpty();
                String fragment = pageStart ? "" : "#" + chunk.headingId();
                String kind = !chunk.kind().isEmpty() ? chunk.kind() : pageStart ? pageKind : "section";
                records.add(new SearchRecord(
                        rendered.page().url() + fragment,
                        rendered.page().title(),
                        section,
                        chunk.heading(),
                        api ? "" : chunk.text(),
                        pageStart ? keywords : "",
                        kind,
                        area));
            }
        }
        return records;
    }

    private String sectionTitle(String sectionId) {
        return site.sections().stream()
                .filter(section -> section.id().equals(sectionId))
                .map(section -> section.title())
                .findFirst()
                .orElse("");
    }
}
