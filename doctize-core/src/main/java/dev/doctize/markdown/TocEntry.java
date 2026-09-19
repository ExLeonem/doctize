package dev.doctize.markdown;

/** An entry of the "on this page" table of contents: one heading of a rendered document. */
public record TocEntry(int level, String text, String id) {}
