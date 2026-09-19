package dev.doctize.integration;

import dev.doctize.Doctize;
import dev.doctize.GenerationReport;
import dev.doctize.testing.Javac;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/** Copies a sample library out of the test resources, compiles it with the annotation processor and documents it. */
final class SampleProject {

    private SampleProject() {}

    /** Copies {@code src/integrationTest/resources/<name>} to {@code <target>/<name>}. */
    static Path copy(String name, Path target) {
        try {
            Path source = Path.of(SampleProject.class.getResource("/" + name).toURI());
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

    /** Compiles {@code <project>/java} with the processor; returns the class output directory holding the model. */
    static Path compile(Path project) {
        String classpath = System.getProperty("java.class.path");
        Path classes = project.resolve("build/classes");
        Javac.compile(project.resolve("java"), classes, classpath, classpath);
        return classes;
    }

    /** Documents a compiled project with its own {@code doctize.yml}; {@code customize} may adjust the builder. */
    static GenerationReport generate(Path project, Path classes, Path output, UnaryOperator<Doctize.Builder> customize) {
        Doctize.Builder builder = Doctize.builder().configFile(project.resolve("doctize.yml")).model(classes).output(output);
        return customize.apply(builder).build().generate();
    }
}
