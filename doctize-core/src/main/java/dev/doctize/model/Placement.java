package dev.doctize.model;

import dev.doctize.annotations.Doc;

/**
 * Where an element wants to be placed in the documentation, as requested by its annotation.
 * Blank values mean "use the default".
 */
@Doc(group = "Source model", order = 44)
public record Placement(String section, String title, int order, String group) {

    public static final int DEFAULT_ORDER = 100;

    public Placement {
        section = section == null ? "" : section;
        title = title == null ? "" : title;
        group = group == null ? "" : group;
    }

    public static Placement none() {
        return new Placement("", "", DEFAULT_ORDER, "");
    }
}
