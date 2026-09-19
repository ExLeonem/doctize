package dev.doctize.model;

import dev.doctize.annotations.Doc;

/** The kinds of Java elements that can appear in the documentation. */
@Doc(group = "Source model", order = 46)
public enum ElementKind {
    PACKAGE("package"),
    CLASS("class"),
    INTERFACE("interface"),
    ENUM("enum"),
    RECORD("record"),
    ANNOTATION_TYPE("annotation"),
    CONSTRUCTOR("constructor"),
    METHOD("method"),
    FIELD("field"),
    ENUM_CONSTANT("enum constant");

    private final String label;

    ElementKind(String label) {
        this.label = label;
    }

    /** Human-readable name, e.g. {@code "annotation"}. */
    public String label() {
        return label;
    }

    public boolean isType() {
        return switch (this) {
            case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> true;
            default -> false;
        };
    }
}
