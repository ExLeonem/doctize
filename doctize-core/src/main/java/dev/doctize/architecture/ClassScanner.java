package dev.doctize.architecture;

import dev.doctize.DoctizeException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads class files, from directories and jars, and finds which classes each one refers to.
 *
 * <p>Everything a class file says about other classes ends up in its constant pool: as a class, or inside a descriptor
 * or a generic signature. So the pool is read instead of every instruction, which finds super types, fields, parameters,
 * generics, exceptions, annotations and calls alike.
 */
final class ClassScanner {

    /** A class name in a descriptor or signature: {@code Lcom/acme/Cache<TK;>;} mentions {@code com/acme/Cache}. */
    private static final Pattern CLASS_IN_DESCRIPTOR = Pattern.compile("L([A-Za-z0-9_$/]+)[;<]");

    private ClassScanner() {}

    /** Scans every class below the given directories and jars. Locations that do not exist are skipped. */
    static List<ScannedClass> scan(List<Path> roots) {
        Map<String, ScannedClass> classes = new LinkedHashMap<>();
        for (Path root : roots) {
            try {
                if (Files.isDirectory(root)) {
                    scanDirectory(root, classes);
                } else if (Files.isRegularFile(root) && root.getFileName().toString().endsWith(".jar")) {
                    scanJar(root, classes);
                }
            } catch (IOException e) {
                throw new DoctizeException("Cannot read the classes in " + root + ": " + e.getMessage(), e);
            }
        }
        return List.copyOf(classes.values());
    }

    private static void scanDirectory(Path directory, Map<String, ScannedClass> classes) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(directory)) {
            files = walk.filter(path -> path.toString().endsWith(".class")).sorted().toList();
        }
        for (Path file : files) {
            add(Files.readAllBytes(file), file.toString(), classes);
        }
    }

    private static void scanJar(Path jar, Map<String, ScannedClass> classes) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<? extends ZipEntry> entries = zip.stream()
                    .filter(entry -> entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/"))
                    .toList();
            for (ZipEntry entry : entries) {
                try (InputStream in = zip.getInputStream(entry)) {
                    add(in.readAllBytes(), jar + "!/" + entry.getName(), classes);
                }
            }
        }
    }

    private static void add(byte[] bytes, String origin, Map<String, ScannedClass> classes) {
        ClassModel model;
        try {
            model = ClassFile.of().parse(bytes);
        } catch (RuntimeException e) {
            throw new DoctizeException("Cannot read the class file " + origin + ": " + e.getMessage(), e);
        }
        String name = model.thisClass().asInternalName();
        if (name.equals("module-info") || name.endsWith("/package-info")) {
            return;
        }
        Set<String> references = new HashSet<>();
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof ClassEntry classEntry) {
                addClassName(classEntry.asInternalName(), references);
            } else if (entry instanceof Utf8Entry utf8) {
                addFromDescriptor(utf8.stringValue(), references);
            }
        }
        String binaryName = name.replace('/', '.');
        references.remove(binaryName);
        int lastDot = binaryName.lastIndexOf('.');
        classes.putIfAbsent(binaryName, new ScannedClass(binaryName, lastDot < 0 ? "" : binaryName.substring(0, lastDot), references));
    }

    /** A class entry names a class, or for an array type gives its descriptor. */
    private static void addClassName(String internalName, Set<String> references) {
        if (internalName.startsWith("[")) {
            addFromDescriptor(internalName, references);
        } else {
            references.add(internalName.replace('/', '.'));
        }
    }

    /** Only strings that look like a descriptor or signature are read; names of members and strings are not. */
    private static void addFromDescriptor(String text, Set<String> references) {
        if (text.isEmpty() || text.indexOf(' ') >= 0 || "(L[<".indexOf(text.charAt(0)) < 0) {
            return;
        }
        Matcher matcher = CLASS_IN_DESCRIPTOR.matcher(text);
        while (matcher.find()) {
            references.add(matcher.group(1).replace('/', '.'));
        }
    }

    /** Merges the nested classes of a class into it: {@code A$B} and {@code A$B$C} are part of {@code A}. */
    static List<ScannedClass> mergeNestedClasses(List<ScannedClass> classes) {
        Set<String> names = new HashSet<>();
        classes.forEach(scanned -> names.add(scanned.name()));
        Map<String, String> owner = new LinkedHashMap<>();
        for (ScannedClass scanned : classes) {
            owner.put(scanned.name(), outermost(scanned.name(), names));
        }
        Map<String, Set<String>> mergedReferences = new LinkedHashMap<>();
        Map<String, String> packages = new LinkedHashMap<>();
        for (ScannedClass scanned : classes) {
            String outer = owner.get(scanned.name());
            Set<String> references = mergedReferences.computeIfAbsent(outer, key -> new HashSet<>());
            for (String reference : scanned.references()) {
                references.add(owner.getOrDefault(reference, reference));
            }
            packages.putIfAbsent(outer, scanned.packageName());
        }
        List<ScannedClass> merged = new ArrayList<>();
        mergedReferences.forEach((name, references) -> {
            references.remove(name);
            merged.add(new ScannedClass(name, packages.get(name), references));
        });
        return List.copyOf(merged);
    }

    private static String outermost(String name, Set<String> known) {
        String current = name;
        int dollar = current.lastIndexOf('$');
        while (dollar > 0) {
            String candidate = current.substring(0, dollar);
            if (known.contains(candidate)) {
                current = candidate;
            }
            dollar = current.lastIndexOf('$', dollar - 1);
        }
        return current;
    }
}
