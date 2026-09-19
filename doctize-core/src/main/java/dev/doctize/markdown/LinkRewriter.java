package dev.doctize.markdown;

/** Adapts link and image destinations written in Markdown to the generated site. */
public interface LinkRewriter {

    /** @return the destination to emit for a link, given as written in the source */
    String link(String destination);

    /** @return the destination to emit for an image, given as written in the source */
    String image(String destination);

    static LinkRewriter identity() {
        return new LinkRewriter() {
            @Override
            public String link(String destination) {
                return destination;
            }

            @Override
            public String image(String destination) {
                return destination;
            }
        };
    }
}
