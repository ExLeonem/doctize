package dev.doctize.diagram;

import dev.doctize.site.PageContent;
import dev.doctize.site.Site;
import dev.doctize.site.SitePage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.regex.Pattern;

/**
 * The Mermaid library that draws the diagrams in the browser. It is large, so a site only gets a copy if one of its pages has a
 * diagram, and a page only loads it if it shows one.
 */
public final class MermaidAssets {

    /** Where the script is in the generated site. */
    public static final String SITE_PATH = "assets/vendor/mermaid.min.js";

    private static final String RESOURCE = "mermaid.min.js";
    private static final Pattern FENCE = Pattern.compile("(?m)^ {0,3}(?:`{3,}|~{3,})\\s*mermaid(?:\\s|$)");

    private MermaidAssets() {}

    /** Whether any page of the site contains a Mermaid diagram, a fenced code block with the language {@code mermaid}. */
    public static boolean usedBy(Site site) {
        for (SitePage page : site.pages()) {
            if (page.page().content() instanceof PageContent.Guide guide && containsDiagram(guide.markdown())) {
                return true;
            }
        }
        return false;
    }

    static boolean containsDiagram(String markdown) {
        return FENCE.matcher(markdown).find();
    }

    /** The Mermaid script, as bundled with Doctize. */
    public static byte[] script() {
        try (InputStream in = MermaidAssets.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the Doctize library");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
