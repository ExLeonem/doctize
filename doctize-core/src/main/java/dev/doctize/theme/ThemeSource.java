package dev.doctize.theme;

import java.util.List;
import java.util.Optional;

/** A place theme files can be read from: a directory on disk or a classpath location. */
interface ThemeSource {

    /** @param path relative path such as {@code templates/layout.mustache} */
    Optional<byte[]> read(String path);

    /** All files below {@code directory}, as paths relative to it, using {@code /}. */
    List<String> list(String directory);
}
