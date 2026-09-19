package dev.doctize.model;

import dev.doctize.annotations.Doc;
import java.util.List;

/**
 * The structural facts about a Java element, independent of its documentation comment.
 *
 * @param kind the kind of element
 * @param name the simple name
 * @param qualifiedName the fully qualified name; members are qualified by their owner
 * @param packageName the package the element lives in
 * @param signature the declaration as Java source, with simple type names
 * @param annotations the source form of the annotations present, e.g. {@code @Deprecated}
 * @param parameters the parameters of a method or constructor, empty otherwise
 * @param returnType the return type of a method, {@code null} otherwise
 * @param thrownTypes the declared exception types of a method or constructor
 */
@Doc(group = "Source model", order = 42)
public record Declaration(
        ElementKind kind,
        String name,
        String qualifiedName,
        String packageName,
        String signature,
        List<String> annotations,
        List<Parameter> parameters,
        String returnType,
        List<String> thrownTypes) {

    public Declaration {
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        thrownTypes = thrownTypes == null ? List.of() : List.copyOf(thrownTypes);
        packageName = packageName == null ? "" : packageName;
    }

    public boolean isDeprecated() {
        return annotations.stream().anyMatch(a -> a.equals("@Deprecated") || a.startsWith("@Deprecated("));
    }
}
