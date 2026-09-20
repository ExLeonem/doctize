package dev.doctize.architecture;

import dev.doctize.DoctizeException;
import java.io.IOException;
import java.io.InputStream;
import dev.doctize.architecture.TypeInfo.AnnotationInfo;
import dev.doctize.architecture.TypeInfo.Call;
import dev.doctize.architecture.TypeInfo.ClassValue;
import dev.doctize.architecture.TypeInfo.EnumValue;
import dev.doctize.architecture.TypeInfo.FieldInfo;
import dev.doctize.architecture.TypeInfo.MethodInfo;
import dev.doctize.architecture.TypeInfo.RecordComponentInfo;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
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

    /** What a scan found: the types, and the annotations that are on packages ({@code package-info}), by package name. */
    record Scan(List<TypeInfo> types, Map<String, List<AnnotationInfo>> packageAnnotations) {}

    /** Scans every class below the given directories and jars, and reports which class refers to which. */
    static List<ScannedClass> scan(List<Path> roots) {
        return scanTypes(roots).types().stream()
                .map(type -> new ScannedClass(type.name(), type.packageName(), type.references()))
                .toList();
    }

    /** Scans every class below the given directories and jars. Locations that do not exist are skipped. */
    static Scan scanTypes(List<Path> roots) {
        Map<String, TypeInfo> types = new LinkedHashMap<>();
        Map<String, List<AnnotationInfo>> packages = new LinkedHashMap<>();
        for (Path root : roots) {
            try {
                if (Files.isDirectory(root)) {
                    scanDirectory(root, types, packages);
                } else if (Files.isRegularFile(root) && root.getFileName().toString().endsWith(".jar")) {
                    scanJar(root, types, packages);
                }
            } catch (IOException e) {
                throw new DoctizeException("Cannot read the classes in " + root + ": " + e.getMessage(), e);
            }
        }
        return new Scan(List.copyOf(types.values()), packages);
    }

    private static void scanDirectory(Path directory, Map<String, TypeInfo> types, Map<String, List<AnnotationInfo>> packages) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(directory)) {
            files = walk.filter(path -> path.toString().endsWith(".class")).sorted().toList();
        }
        for (Path file : files) {
            add(Files.readAllBytes(file), file.toString(), types, packages);
        }
    }

    private static void scanJar(Path jar, Map<String, TypeInfo> types, Map<String, List<AnnotationInfo>> packages) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<? extends ZipEntry> entries = zip.stream()
                    .filter(entry -> entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/"))
                    .toList();
            for (ZipEntry entry : entries) {
                try (InputStream in = zip.getInputStream(entry)) {
                    add(in.readAllBytes(), jar + "!/" + entry.getName(), types, packages);
                }
            }
        }
    }

    private static void add(byte[] bytes, String origin, Map<String, TypeInfo> types, Map<String, List<AnnotationInfo>> packages) {
        ClassModel model;
        try {
            model = ClassFile.of().parse(bytes);
        } catch (RuntimeException e) {
            throw new DoctizeException("Cannot read the class file " + origin + ": " + e.getMessage(), e);
        }
        String internalName = model.thisClass().asInternalName();
        if (internalName.equals("module-info")) {
            return;
        }
        if (internalName.endsWith("/package-info") || internalName.equals("package-info")) {
            int slash = internalName.lastIndexOf('/');
            packages.putIfAbsent(slash < 0 ? "" : internalName.substring(0, slash).replace('/', '.'), annotationsOf(model));
            return;
        }
        TypeInfo type = read(model, internalName);
        types.putIfAbsent(type.name(), type);
    }

    private static TypeInfo read(ClassModel model, String internalName) {
        String binaryName = internalName.replace('/', '.');
        Set<String> references = new HashSet<>();
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof ClassEntry classEntry) {
                addClassName(classEntry.asInternalName(), references);
            } else if (entry instanceof Utf8Entry utf8) {
                addFromDescriptor(utf8.stringValue(), references);
            }
        }
        references.remove(binaryName);
        int lastDot = binaryName.lastIndexOf('.');

        int flags = model.flags().flagsMask();
        int nestedFlags = flags;
        String outer = null;
        boolean anonymous = false;
        for (InnerClassInfo inner : model.findAttribute(Attributes.innerClasses()).map(attribute -> attribute.classes()).orElse(List.of())) {
            if (inner.innerClass().asInternalName().equals(internalName)) {
                nestedFlags = inner.flagsMask();
                outer = inner.outerClass().map(entry -> entry.asInternalName().replace('/', '.')).orElse(null);
                anonymous = inner.innerName().isEmpty() || inner.outerClass().isEmpty();
            }
        }
        List<String> typeParameters = model.findAttribute(Attributes.signature())
                .map(attribute -> Signatures.type(attribute.signature().stringValue()).typeParameters())
                .orElse(List.of());

        List<FieldInfo> fields = new ArrayList<>();
        for (FieldModel field : model.fields()) {
            fields.add(new FieldInfo(
                    field.fieldName().stringValue(),
                    field.fieldType().stringValue(),
                    signatureOf(field),
                    field.flags().flagsMask(),
                    annotationsOf(field)));
        }
        List<MethodInfo> methods = new ArrayList<>();
        for (MethodModel method : model.methods()) {
            methods.add(new MethodInfo(
                    method.methodName().stringValue(),
                    method.methodType().stringValue(),
                    signatureOf(method),
                    method.flags().flagsMask(),
                    parameterNames(method),
                    method.findAttribute(Attributes.exceptions())
                            .map(attribute -> attribute.exceptions().stream().map(entry -> entry.asInternalName().replace('/', '.')).toList())
                            .orElse(List.of()),
                    annotationsOf(method),
                    callsOf(method)));
        }
        List<RecordComponentInfo> components = new ArrayList<>();
        model.findAttribute(Attributes.record()).ifPresent(record -> record.components().forEach(component -> components.add(
                new RecordComponentInfo(component.name().stringValue(), component.descriptor().stringValue(), signatureOf(component)))));

        return new TypeInfo(
                binaryName,
                lastDot < 0 ? "" : binaryName.substring(0, lastDot),
                flags,
                nestedFlags,
                outer,
                anonymous,
                model.superclass().map(entry -> entry.asInternalName().replace('/', '.')).orElse(null),
                model.interfaces().stream().map(entry -> entry.asInternalName().replace('/', '.')).toList(),
                typeParameters,
                fields,
                methods,
                components,
                annotationsOf(model),
                references);
    }

    private static String signatureOf(AttributedElement element) {
        return element.findAttribute(Attributes.signature()).map(attribute -> attribute.signature().stringValue()).orElse(null);
    }

    private static List<String> parameterNames(MethodModel method) {
        return method.findAttribute(Attributes.methodParameters())
                .map(attribute -> attribute.parameters().stream()
                        .map(parameter -> parameter.name().map(Utf8Entry::stringValue).orElse(""))
                        .toList())
                .orElse(List.of());
    }

    /** The calls in a method, in code order. A lambda or method reference counts as a call to the method it runs. */
    private static List<Call> callsOf(MethodModel method) {
        List<Call> calls = new ArrayList<>();
        method.code().ifPresent(code -> {
            for (CodeElement element : code) {
                if (element instanceof InvokeInstruction invoke) {
                    calls.add(new Call(invoke.owner().asInternalName().replace('/', '.'), invoke.name().stringValue(), invoke.type().stringValue()));
                } else if (element instanceof InvokeDynamicInstruction dynamic) {
                    for (var argument : dynamic.invokedynamic().bootstrap().arguments()) {
                        if (argument instanceof MethodHandleEntry handle) {
                            MemberRefEntry target = handle.reference();
                            calls.add(new Call(target.owner().asInternalName().replace('/', '.'), target.name().stringValue(), target.type().stringValue()));
                        }
                    }
                }
            }
        });
        return calls;
    }

    private static List<AnnotationInfo> annotationsOf(AttributedElement element) {
        List<AnnotationInfo> annotations = new ArrayList<>();
        element.findAttribute(Attributes.runtimeVisibleAnnotations()).ifPresent(a -> a.annotations().forEach(x -> annotations.add(annotation(x))));
        element.findAttribute(Attributes.runtimeInvisibleAnnotations()).ifPresent(a -> a.annotations().forEach(x -> annotations.add(annotation(x))));
        return flattenContainers(annotations);
    }

    /** Several repeated annotations are stored inside a container annotation; they are read as if they stood on their own. */
    private static List<AnnotationInfo> flattenContainers(List<AnnotationInfo> annotations) {
        List<AnnotationInfo> flat = new ArrayList<>();
        for (AnnotationInfo annotation : annotations) {
            if (annotation.type().equals(Marks.TRANSITIONS) && annotation.values().get("value") instanceof List<?> nested) {
                nested.stream().filter(AnnotationInfo.class::isInstance).map(AnnotationInfo.class::cast).forEach(flat::add);
            } else {
                flat.add(annotation);
            }
        }
        return flat;
    }

    private static AnnotationInfo annotation(java.lang.classfile.Annotation annotation) {
        Map<String, Object> values = new LinkedHashMap<>();
        annotation.elements().forEach(element -> values.put(element.name().stringValue(), value(element.value())));
        return new AnnotationInfo(typeName(annotation.className().stringValue()), values);
    }

    private static Object value(AnnotationValue value) {
        return switch (value) {
            case AnnotationValue.OfString string -> string.stringValue();
            case AnnotationValue.OfBoolean bool -> bool.booleanValue();
            case AnnotationValue.OfInt integer -> integer.intValue();
            case AnnotationValue.OfLong number -> number.longValue();
            case AnnotationValue.OfDouble number -> number.doubleValue();
            case AnnotationValue.OfFloat number -> number.floatValue();
            case AnnotationValue.OfShort number -> (int) number.shortValue();
            case AnnotationValue.OfByte number -> (int) number.byteValue();
            case AnnotationValue.OfChar character -> String.valueOf(character.charValue());
            case AnnotationValue.OfEnum enumeration -> new EnumValue(typeName(enumeration.className().stringValue()), enumeration.constantName().stringValue());
            case AnnotationValue.OfClass type -> new ClassValue(typeName(type.className().stringValue()));
            case AnnotationValue.OfArray array -> array.values().stream().map(ClassScanner::value).toList();
            case AnnotationValue.OfAnnotation nested -> annotation(nested.annotation());
        };
    }

    /** {@code Lcom/acme/Foo;} as {@code com.acme.Foo}; other descriptors ({@code V}, {@code I}) as their keyword. */
    private static String typeName(String descriptor) {
        return Signatures.field(descriptor).name();
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
