package dev.doctize.model;

import dev.doctize.DoctizeException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads a {@link SourceModel} from wherever the annotation processor left it: a model file, a
 * class output directory ({@code build/classes/java/main}) or a jar.
 */
public final class ModelLocator {

    private ModelLocator() {}

    public static SourceModel load(Path location) {
        try {
            if (Files.isDirectory(location)) {
                return loadFromFile(location.resolve(SourceModelJson.RESOURCE_PATH));
            }
            if (location.getFileName().toString().endsWith(".json")) {
                return loadFromFile(location);
            }
            return loadFromArchive(location);
        } catch (IOException e) {
            throw new DoctizeException("Cannot read documentation model from " + location + ": " + e.getMessage(), e);
        }
    }

    private static SourceModel loadFromFile(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new DoctizeException("No documentation model found at " + file
                    + ". Is the doctize-processor on the annotation processor path?");
        }
        try (InputStream in = Files.newInputStream(file)) {
            return SourceModelJson.read(in);
        }
    }

    private static SourceModel loadFromArchive(Path archive) throws IOException {
        if (!Files.isRegularFile(archive)) {
            throw new DoctizeException("Model location does not exist: " + archive);
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(SourceModelJson.RESOURCE_PATH);
            if (entry == null) {
                throw new DoctizeException(archive + " does not contain " + SourceModelJson.RESOURCE_PATH);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return SourceModelJson.read(in);
            }
        }
    }
}
