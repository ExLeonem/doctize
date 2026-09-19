package dev.doctize.markdown;

/**
 * The plain text below one heading of a document; the unit that is indexed for search.
 *
 * @param headingId anchor of the heading, empty for the text before the first heading
 * @param heading the heading text, empty for the text before the first heading
 * @param text the plain text of the chunk
 */
public record TextChunk(String headingId, String heading, String text) {}
