package dev.doctize.theme;

import dev.doctize.DoctizeException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** A theme packaged as classpath resources, e.g. inside the Doctize jar. */
final class ClasspathThemeSource implements ThemeSource {

    private final ClassLoader loader;
    private final String base;

    ClasspathThemeSource(ClassLoader loader, String base) {
        this.loader = loader;
        this.base = base;
    }

    @Override
    public Optional<byte[]> read(String path) {
        try (InputStream in = loader.getResourceAsStream(base + "/" + path)) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new DoctizeException("Cannot read theme resource " + base + "/" + path, e);
        }
    }

    @Override
    public List<String> list(String directory) {
        URL url = loader.getResource(base + "/" + directory);
        if (url == null) {
            return List.of();
        }
        try {
            URI uri = url.toURI();
            if (!"jar".equals(uri.getScheme())) {
                return listFiles(Path.of(uri));
            }
            try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                return listFiles(jar.provider().getPath(uri));
            } catch (FileSystemAlreadyExistsException alreadyOpen) {
                return listFiles(FileSystems.getFileSystem(uri).provider().getPath(uri));
            }
        } catch (URISyntaxException | IOException e) {
            throw new DoctizeException("Cannot list theme resources in " + base + "/" + directory, e);
        }
    }

    private static List<String> listFiles(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile)
                    .map(file -> directory.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }
}
