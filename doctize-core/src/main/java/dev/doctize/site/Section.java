package dev.doctize.site;

import java.util.List;

/** A section of the site with its pages in navigation order. */
public record Section(String id, String title, String description, List<SitePage> pages) {

    public Section {
        pages = List.copyOf(pages);
    }
}
