package dev.doctize.site;

/**
 * A page placed in the site.
 *
 * @param page the page
 * @param section the id of the section the page belongs to
 * @param url the location relative to the site root, e.g. {@code quick-start/install.html}
 */
public record SitePage(Page page, String section, String url) {

    public String title() {
        return page.title();
    }
}
