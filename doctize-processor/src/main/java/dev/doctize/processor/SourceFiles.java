package dev.doctize.processor;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import dev.doctize.model.SourceLocation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/** Where elements are declared: their source files on disk, and their locations in the Git repository. */
final class SourceFiles {

    private final Trees trees;
    private final Map<Path, Optional<Path>> repositoryRoots = new HashMap<>();

    SourceFiles(ProcessingEnvironment environment) {
        this.trees = trees(environment);
    }

    /** The source file of the element, if it is a file on disk. */
    Optional<Path> file(Element element) {
        return treePath(element).flatMap(path -> file(path.getCompilationUnit()));
    }

    /**
     * The file relative to the root of its Git repository, and the line the declaration starts on. Only the relative path
     * is kept, so that the model does not depend on where the project was checked out.
     */
    Optional<SourceLocation> location(Element element) {
        Optional<TreePath> treePath = treePath(element);
        if (treePath.isEmpty()) {
            return Optional.empty();
        }
        CompilationUnitTree unit = treePath.get().getCompilationUnit();
        Optional<Path> file = file(unit);
        Optional<Path> root = file.flatMap(this::repositoryRoot);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        Tree tree = treePath.get().getLeaf();
        long position = trees.getSourcePositions().getStartPosition(unit, tree);
        if (position == Diagnostic.NOPOS) {
            return Optional.empty();
        }
        String relative = root.get().relativize(file.get()).toString().replace('\\', '/');
        return Optional.of(new SourceLocation(relative, (int) unit.getLineMap().getLineNumber(position)));
    }

    private Optional<TreePath> treePath(Element element) {
        return trees == null ? Optional.empty() : Optional.ofNullable(trees.getPath(element));
    }

    private static Optional<Path> file(CompilationUnitTree unit) {
        URI uri = unit.getSourceFile().toUri();
        return "file".equals(uri.getScheme()) ? Optional.of(Path.of(uri).toAbsolutePath().normalize()) : Optional.empty();
    }

    /** The nearest directory with a {@code .git} directory, or a {@code .git} file in a worktree or submodule. */
    private Optional<Path> repositoryRoot(Path file) {
        return repositoryRoots.computeIfAbsent(file.getParent(), directory -> {
            for (Path current = directory; current != null; current = current.getParent()) {
                if (Files.exists(current.resolve(".git"))) {
                    return Optional.of(current);
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Only javac has syntax trees; with another compiler, locations are unknown. Gradle wraps javac's environment for
     * incremental processing, so the wrapped one is looked for as well.
     */
    private static Trees trees(ProcessingEnvironment environment) {
        for (ProcessingEnvironment current = environment; current != null; current = delegateOf(current)) {
            try {
                return Trees.instance(current);
            } catch (IllegalArgumentException e) {
                // not javac's own environment
            }
        }
        return null;
    }

    private static ProcessingEnvironment delegateOf(ProcessingEnvironment wrapper) {
        for (Class<?> type = wrapper.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (ProcessingEnvironment.class.isAssignableFrom(field.getType()) && !Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        Object delegate = field.get(wrapper);
                        if (delegate != wrapper && delegate instanceof ProcessingEnvironment environment) {
                            return environment;
                        }
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
}
