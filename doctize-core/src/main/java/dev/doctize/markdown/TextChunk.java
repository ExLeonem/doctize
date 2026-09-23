package dev.doctize.markdown;

/**
 * The plain text below one heading of a document; the unit that is indexed for search.
 *
 * @param headingId anchor of the heading, empty for the text before the first heading
 * @param heading the heading text, empty for the text before the first heading
 * @param text the plain text of the chunk
 * @param kind what the heading stands for, e.g. {@code "method"}; empty for an ordinary heading
 */
public record TextChunk(String headingId, String heading, String text, String kind) {

    public TextChunk(String headingId, String heading, String text) {
        this(headingId, heading, text, "");
    }
}
