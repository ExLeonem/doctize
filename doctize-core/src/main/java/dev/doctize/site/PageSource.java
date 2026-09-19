package dev.doctize.site;

import dev.doctize.annotations.Doc;
import java.util.List;

/**
 * A provider of pages. Doctize ships sources for Java API elements and Markdown directories;
 * implement this interface to contribute pages from anywhere else, e.g. a generated changelog.
 */
@FunctionalInterface
@Doc(group = "Extending", order = 30)
public interface PageSource {

    List<Page> pages();
}
