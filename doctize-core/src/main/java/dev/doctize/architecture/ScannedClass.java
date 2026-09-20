package dev.doctize.architecture;

import java.util.Set;

/**
 * A class of the project, as found in its bytecode.
 *
 * @param name the binary name, e.g. {@code com.acme.Cache$Entry}
 * @param packageName the package, empty for the default package
 * @param references the binary names of every class it mentions: as super type, in signatures, fields, method bodies,
 *     annotations and so on
 */
record ScannedClass(String name, String packageName, Set<String> references) {

    ScannedClass {
        references = Set.copyOf(references);
    }
}
