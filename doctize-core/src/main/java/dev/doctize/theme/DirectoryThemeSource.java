package dev.doctize.theme;

import dev.doctize.DoctizeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** A theme stored in a directory. */
final class DirectoryThemeSource implements ThemeSource {

    private final Path root;

    DirectoryThemeSource(Path root) {
        this.root = root;
    }

    @Override
    public Optional<byte[]> read(String path) {
        Path file = root.resolve(path);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new DoctizeException("Cannot read theme file " + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> list(String directory) {
        Path base = root.resolve(directory);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(Files::isRegularFile)
                    .map(file -> base.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new DoctizeException("Cannot read theme directory " + base + ": " + e.getMessage(), e);
        }
    }
}
