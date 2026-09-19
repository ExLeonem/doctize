package dev.doctize.system;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Copies a sample library from the shared test resources. */
final class SamplesForSystemTests {

    private SamplesForSystemTests() {}

    static Path copy(String name, Path target) {
        try {
            Path source = Path.of(SamplesForSystemTests.class.getResource("/" + name).toURI());
            Path destination = target.resolve(name);
            try (Stream<Path> walk = Files.walk(source)) {
                for (Path file : walk.toList()) {
                    Path copy = destination.resolve(source.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.createDirectories(copy.getParent());
                        Files.copy(file, copy);
                    }
                }
            }
            return destination;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
