package dev.doctize.model;

import dev.doctize.annotations.Doc;
import java.util.List;

/**
 * Everything the annotation processor extracted from one compilation.
 *
 * @param formatVersion version of the serialized format, see {@link #CURRENT_VERSION}
 * @param elements the annotated top-level elements
 */
@Doc(group = "Source model", order = 40)
public record SourceModel(int formatVersion, List<SourceElement> elements) {

    public static final int CURRENT_VERSION = 1;

    public SourceModel {
        elements = elements == null ? List.of() : List.copyOf(elements);
    }

    public static SourceModel of(List<SourceElement> elements) {
        return new SourceModel(CURRENT_VERSION, elements);
    }
}
