package dev.doctize.render;

import java.util.List;

/** Recognises references to classes of the JDK, which are never documented here and are not worth a warning. */
final class JdkTypes {

    /** Packages whose classes are commonly referred to by simple name, as if imported. */
    private static final List<String> COMMON_PACKAGES = List.of(
            "", "java.lang.", "java.util.", "java.util.function.", "java.util.concurrent.", "java.util.stream.",
            "java.io.", "java.nio.file.", "java.time.", "java.net.");

    private JdkTypes() {}

    /** @param reference a link reference such as {@code String}, {@code java.util.List#of()} or {@code #size} */
    static boolean isKnown(String reference) {
        int hash = reference.indexOf('#');
        String type = (hash < 0 ? reference : reference.substring(0, hash)).strip();
        return !type.isEmpty() && COMMON_PACKAGES.stream().anyMatch(prefix -> exists(prefix + type));
    }

    private static boolean exists(String className) {
        try {
            Class.forName(className, false, ClassLoader.getPlatformClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
