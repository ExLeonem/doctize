package dev.doctize.search;

/**
 * One searchable unit: a page, or one heading of a page with the text below it.
 *
 * @param url location including the fragment, relative to the site root
 * @param title the title of the page
 * @param section the title of the page's section
 * @param heading the heading of this unit, empty for the start of a page
 * @param text the plain text of this unit
 * @param keywords extra terms that find the unit
 * @param kind what the unit is, shown as a tag on the result: {@code page} or {@code section} in a guide; on an API
 *     page the kind of the type or member, e.g. {@code class}, {@code method}, {@code field}
 * @param area {@value #API} for API pages, {@value #GUIDES} for all others; the search can be limited to one
 */
public record SearchRecord(
        String url, String title, String section, String heading, String text, String keywords, String kind, String area) {

    public static final String API = "api";
    public static final String GUIDES = "guides";
}
