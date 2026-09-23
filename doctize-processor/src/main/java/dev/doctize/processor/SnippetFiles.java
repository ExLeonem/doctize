package dev.doctize.processor;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import dev.doctize.javadoc.SnippetTag;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 * Reads the external files of {@code {@snippet file=...}} and {@code {@snippet class=...}} tags, like the javadoc tool:
 * from the {@code snippet-files} directory next to the source file, then from the directories of the
 * {@value #PATH_OPTION} option. They are read at compile time because the generator has no access to the sources.
 */
final class SnippetFiles {

    /** Directories with snippet files, separated like a class path; e.g. {@code -Adoctize.snippetPath=src/test/java}. */
    static final String PATH_OPTION = "doctize.snippetPath";

    private final Trees trees;
    private final List<Path> path;
    private final Messager messager;

    SnippetFiles(ProcessingEnvironment environment) {
        this.trees = trees(environment);
        this.messager = environment.getMessager();
        String option = environment.getOptions().getOrDefault(PATH_OPTION, "");
        this.path = Arrays.stream(option.split(File.pathSeparator)).filter(dir -> !dir.isBlank()).map(Path::of).toList();
    }

    /** The files shown by the snippet tags of {@code comment}, keyed by {@link SnippetTag#externalReference()}. */
    Map<String, String> referencedBy(Element element, String comment) {
        if (!comment.contains("{@snippet")) {
            return Map.of();
        }
        Map<String, String> files = new LinkedHashMap<>();
        for (SnippetTag tag : SnippetTag.findAll(comment)) {
            Optional<String> reference = tag.externalReference();
            if (reference.isEmpty() || files.containsKey(reference.get())) {
                continue;
            }
            Optional<String> content = read(element, relativePath(reference.get()));
            if (content.isPresent()) {
                files.put(reference.get(), content.get());
            } else {
                messager.printMessage(Diagnostic.Kind.WARNING, "Doctize: the snippet " + reference.get().replaceFirst(":", " ")
                        + " was not found in the snippet-files directory of the package or in -A" + PATH_OPTION, element);
            }
        }
        return files;
    }

    /** {@code class:com.acme.Example} → {@code com/acme/Example.java}; {@code file:} paths as they are. */
    private static String relativePath(String reference) {
        String value = reference.substring(reference.indexOf(':') + 1);
        return reference.startsWith("class:") ? value.replace('.', '/') + ".java" : value;
    }

    private Optional<String> read(Element element, String relative) {
        List<Path> candidates = new ArrayList<>();
        packageDirectory(element).ifPresent(dir -> candidates.add(dir.resolve("snippet-files").resolve(relative)));
        path.forEach(dir -> candidates.add(dir.resolve(relative)));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try {
                    return Optional.of(Files.readString(candidate).replace("\r\n", "\n"));
                } catch (IOException e) {
                    messager.printMessage(Diagnostic.Kind.WARNING, "Doctize: cannot read the snippet " + candidate + ": " + e.getMessage(), element);
                }
            }
        }
        return Optional.empty();
    }

    /** The directory of the element's source file, if it is a file on disk. */
    private Optional<Path> packageDirectory(Element element) {
        if (trees == null) {
            return Optional.empty();
        }
        TreePath tree = trees.getPath(element);
        if (tree == null) {
            return Optional.empty();
        }
        var uri = tree.getCompilationUnit().getSourceFile().toUri();
        return "file".equals(uri.getScheme()) ? Optional.ofNullable(Path.of(uri).getParent()) : Optional.empty();
    }

    /** Only javac has syntax trees; with another compiler, only the snippet path is searched. */
    private static Trees trees(ProcessingEnvironment environment) {
        try {
            return Trees.instance(environment);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
