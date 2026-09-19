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
 */
public record SearchRecord(String url, String title, String section, String heading, String text, String keywords) {}
