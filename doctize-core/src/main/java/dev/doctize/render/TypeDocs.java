package dev.doctize.render;

import dev.doctize.markdown.TextChunk;
import dev.doctize.markdown.TocEntry;
import dev.doctize.render.Views.TypeView;
import java.util.List;

/** A documented type ready for rendering, with the table of contents and search text derived from it. */
record TypeDocs(TypeView view, List<TocEntry> toc, List<TextChunk> chunks) {}
