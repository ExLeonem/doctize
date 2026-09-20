package dev.doctize.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** The version this plugin was built as, which the build writes into {@code version.properties}. */
final class PluginVersion {

    private PluginVersion() {}

    static String get() {
        Properties properties = new Properties();
        try (InputStream in = PluginVersion.class.getResourceAsStream("version.properties")) {
            if (in == null) {
                throw new IllegalStateException("version.properties is missing from the Doctize plugin");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties.getProperty("version");
    }
}
