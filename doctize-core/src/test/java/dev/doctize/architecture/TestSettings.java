package dev.doctize.architecture;

import dev.doctize.config.ArchitectureSettings;
import dev.doctize.config.ConfigLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings written the way a user writes them, so that tests read like the configuration they check. */
final class TestSettings {

    private TestSettings() {}

    /**
     * @param yaml what is under {@code architecture:} in {@code doctize.yml}, without indentation; the map is switched on
     */
    static ArchitectureSettings architecture(String yaml) {
        try {
            Path file = Files.createTempFile("doctize-settings", ".yml");
            try {
                Files.writeString(file, "architecture:\n  enabled: true\n" + yaml.indent(2));
                return ConfigLoader.load(file).architecture();
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
