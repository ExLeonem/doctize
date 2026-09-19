package dev.doctize.search;

import dev.doctize.markdown.TextChunk;
import dev.doctize.render.RenderedPage;
import dev.doctize.site.Site;
import java.util.ArrayList;
import java.util.List;

/** Collects the searchable text of all rendered pages, one record per heading. */
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
            boolean startsWithHeading = !rendered.chunks().isEmpty() && !rendered.chunks().getFirst().heading().isEmpty();
            if (startsWithHeading || rendered.chunks().isEmpty()) {
                records.add(new SearchRecord(rendered.page().url(), rendered.page().title(), section, "", rendered.summary(), keywords));
            }
            for (TextChunk chunk : rendered.chunks()) {
                boolean pageStart = chunk.heading().isEmpty();
                String fragment = pageStart ? "" : "#" + chunk.headingId();
                records.add(new SearchRecord(
                        rendered.page().url() + fragment,
                        rendered.page().title(),
                        section,
                        chunk.heading(),
                        chunk.text(),
                        pageStart ? keywords : ""));
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
