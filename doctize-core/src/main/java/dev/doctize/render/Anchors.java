package dev.doctize.render;

import dev.doctize.model.Declaration;
import dev.doctize.model.Parameter;
import dev.doctize.model.TypeNames;
import java.util.stream.Collectors;

/** Stable, readable HTML ids for the members of a type page, e.g. {@code method-get-Object}. */
final class Anchors {

    private Anchors() {}

    static String of(Declaration declaration) {
        String prefix = switch (declaration.kind()) {
            case FIELD -> "field";
            case ENUM_CONSTANT -> "constant";
            case CONSTRUCTOR -> "constructor";
            case METHOD -> "method";
            default -> "type";
        };
        return prefix + "-" + declaration.name() + parameterSuffix(declaration);
    }

    /** The anchor of a member described by its parameter types, e.g. {@code get(Object)} → {@code -Object}. */
    static String parameterSuffix(Declaration declaration) {
        return declaration.parameters().stream()
                .map(Parameter::type)
                .map(Anchors::part)
                .collect(Collectors.joining("-", declaration.parameters().isEmpty() ? "" : "-", ""));
    }

    static String part(String type) {
        return TypeNames.erasure(type).replace("[]", "Array").replace("...", "Array");
    }
}
