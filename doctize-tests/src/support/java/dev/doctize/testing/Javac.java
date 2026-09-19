package dev.doctize.testing;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Compiles a source tree with the real Doctize annotation processor, as a build tool would. */
public final class Javac {

    private static final String PROCESSOR = "dev.doctize.processor.DocProcessor";

    private Javac() {}

    /**
     * @param sources a directory of {@code .java} files
     * @param classes where the class files, and the {@code META-INF/doctize/model.json}, are written
     * @param compileClasspath the class path of the code being compiled (the annotations)
     * @param processorPath where the annotation processor and its dependencies come from
     */
    public static void compile(Path sources, Path classes, String compileClasspath, String processorPath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            Files.createDirectories(classes);
            List<String> options = new ArrayList<>(List.of(
                    "-d", classes.toString(), "-classpath", compileClasspath,
                    "-processorpath", processorPath, "-processor", PROCESSOR, "-Xlint:none"));
            boolean ok = compiler.getTask(new StringWriter(), files, diagnostics, options, null, files.getJavaFileObjectsFromPaths(javaFiles(sources))).call();
            if (!ok) {
                throw new AssertionError("Compilation of " + sources + " failed:\n" + diagnostics.getDiagnostics());
            }
            diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .findFirst()
                    .ifPresent(d -> {
                        throw new AssertionError(d.toString());
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(file -> file.toString().endsWith(".java")).toList();
        }
    }
}
