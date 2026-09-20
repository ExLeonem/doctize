package dev.doctize.license;

import dev.doctize.annotations.Doc;
import java.util.Arrays;
import java.util.Optional;

/**
 * A capability of Doctize that needs a {@link License}. Everything not listed here is free to use.
 */
@Doc(group = "Licensing", order = 60)
public enum Feature {

    /**
     * The architecture diagrams: the overview of the packages, their dependencies, cycles and layer rules, and class diagrams, state
     * diagrams, entity-relationship diagrams and flow charts made from the code. A Pro feature.
     */
    ARCHITECTURE("architecture", "architecture diagrams");

    private final String id;
    private final String title;

    Feature(String id, String title) {
        this.id = id;
        this.title = title;
    }

    /** The name of the feature in a license and in messages, e.g. {@code architecture}. */
    public String id() {
        return id;
    }

    /** How the feature is called in a sentence, e.g. {@code architecture map}. */
    public String title() {
        return title;
    }

    public static Optional<Feature> byId(String id) {
        return Arrays.stream(values()).filter(feature -> feature.id.equalsIgnoreCase(id.strip())).findFirst();
    }
}
