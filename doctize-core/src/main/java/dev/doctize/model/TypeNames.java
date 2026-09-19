package dev.doctize.model;

import java.util.regex.Pattern;

/** Helpers for the textual form of Java types, as reported by the compiler. */
public final class TypeNames {

    private static final Pattern TYPE_ANNOTATION = Pattern.compile("@[\\w.]+(?:\\([^)]*\\))?\\s*");
    private static final Pattern PACKAGE_PREFIX = Pattern.compile("\\b(?:[a-z_][\\w$]*\\.)+(?=[A-Za-z_$])");
    private static final Pattern INNERMOST_GENERICS = Pattern.compile("<[^<>]*>");

    private TypeNames() {}

    /** {@code java.util.List<java.lang.String>} becomes {@code List<String>}; annotations are dropped. */
    public static String simplify(String type) {
        return prettify(stripPackages(TYPE_ANNOTATION.matcher(type).replaceAll("")));
    }

    /** Removes package qualifiers from any Java source text, e.g. {@code @java.lang.Deprecated(since = "1")}. */
    public static String stripPackages(String source) {
        return PACKAGE_PREFIX.matcher(source).replaceAll("").strip();
    }

    /** The compiler prints {@code Map<K,V>} and {@code @Since(a="x")} without spaces; this restores the usual style. */
    public static String prettify(String source) {
        return source.replaceAll(",(?=\\S)", ", ").replaceAll("(?<=\\w)=(?=[\"\\w{@])", " = ");
    }

    /** {@code java.util.Map<K, List<V>>} becomes {@code Map}; array brackets are kept. */
    public static String erasure(String type) {
        String erased = simplify(type);
        String previous;
        do {
            previous = erased;
            erased = INNERMOST_GENERICS.matcher(erased).replaceAll("");
        } while (!erased.equals(previous));
        return erased.replace(" ", "");
    }
}
