package dev.doctize.architecture;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A class, interface, enum, record or annotation of the analyzed code, as its class file describes it.
 *
 * @param name the binary name, e.g. {@code com.acme.Order$Line}
 * @param packageName the package, empty for the default package
 * @param flags the access flags of the class file
 * @param nestedFlags the access flags of a nested class as declared (private, protected, static), which the class file header
 *     does not have; the same as {@code flags} for a top-level class
 * @param outerName the binary name of the class this one is nested in, or {@code null}
 * @param anonymous whether this is an anonymous or local class
 * @param superName the binary name of the super class, or {@code null} for an interface
 * @param interfaces the binary names of the interfaces it implements or extends
 * @param typeParameters the names of its type parameters, e.g. {@code K} and {@code V}
 * @param fields its fields, enum constants included
 * @param methods its methods and constructors
 * @param recordComponents the components of a record, empty otherwise
 * @param annotations its annotations that are kept in the class file
 * @param references every class it mentions
 */
record TypeInfo(
        String name,
        String packageName,
        int flags,
        int nestedFlags,
        String outerName,
        boolean anonymous,
        String superName,
        List<String> interfaces,
        List<String> typeParameters,
        List<FieldInfo> fields,
        List<MethodInfo> methods,
        List<RecordComponentInfo> recordComponents,
        List<AnnotationInfo> annotations,
        Set<String> references) {

    static final int PUBLIC = 0x0001;
    static final int PRIVATE = 0x0002;
    static final int PROTECTED = 0x0004;
    static final int STATIC = 0x0008;
    static final int FINAL = 0x0010;
    static final int VOLATILE_OR_BRIDGE = 0x0040;
    static final int TRANSIENT_OR_VARARGS = 0x0080;
    static final int INTERFACE = 0x0200;
    static final int ABSTRACT = 0x0400;
    static final int SYNTHETIC = 0x1000;
    static final int ANNOTATION = 0x2000;
    static final int ENUM = 0x4000;

    TypeInfo {
        interfaces = List.copyOf(interfaces);
        typeParameters = List.copyOf(typeParameters);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
        recordComponents = List.copyOf(recordComponents);
        annotations = List.copyOf(annotations);
        references = Set.copyOf(references);
    }

    boolean isInterface() {
        return (flags & INTERFACE) != 0 && (flags & ANNOTATION) == 0;
    }

    boolean isAnnotationType() {
        return (flags & ANNOTATION) != 0;
    }

    boolean isEnum() {
        return (flags & ENUM) != 0 && "java.lang.Enum".equals(superName);
    }

    boolean isRecord() {
        return "java.lang.Record".equals(superName);
    }

    boolean isAbstract() {
        return (flags & ABSTRACT) != 0 && (flags & INTERFACE) == 0;
    }

    /** {@code "public"}, {@code "protected"}, {@code "package"} or {@code "private"}. */
    String visibility() {
        return Visibility.of(nestedFlags).label();
    }

    /** The name as written in code: {@code Order.Line}. */
    String displayName() {
        return TypeRef.displayName(name);
    }

    Optional<AnnotationInfo> annotation(String type) {
        return annotations.stream().filter(annotation -> annotation.type().equals(type)).findFirst();
    }

    /**
     * A field, including an enum constant.
     *
     * @param descriptor the JVM descriptor, e.g. {@code Ljava/util/List;}
     * @param signature the generic signature, or {@code null}
     */
    record FieldInfo(String name, String descriptor, String signature, int flags, List<AnnotationInfo> annotations) {

        FieldInfo {
            annotations = List.copyOf(annotations);
        }

        TypeRef type() {
            return Signatures.field(signature != null ? signature : descriptor);
        }

        boolean isEnumConstant() {
            return (flags & ENUM) != 0;
        }

        boolean isStatic() {
            return (flags & STATIC) != 0;
        }

        boolean isSynthetic() {
            return (flags & SYNTHETIC) != 0;
        }

        String visibility() {
            return Visibility.of(flags).label();
        }
    }

    /**
     * A method or constructor.
     *
     * @param name the name; {@code <init>} for a constructor
     * @param descriptor the JVM descriptor, e.g. {@code (Ljava/lang/String;)V}
     * @param signature the generic signature, or {@code null}
     * @param parameterNames the names of the parameters if the code was compiled with {@code -parameters}, otherwise empty
     * @param exceptions the binary names of the exceptions in its {@code throws} clause
     * @param calls the methods it calls, in the order the calls are in the code
     */
    record MethodInfo(
            String name,
            String descriptor,
            String signature,
            int flags,
            List<String> parameterNames,
            List<String> exceptions,
            List<AnnotationInfo> annotations,
            List<Call> calls) {

        MethodInfo {
            parameterNames = List.copyOf(parameterNames);
            exceptions = List.copyOf(exceptions);
            annotations = List.copyOf(annotations);
            calls = List.copyOf(calls);
        }

        Signatures.MethodSignature signatureOf() {
            return Signatures.method(signature != null ? signature : descriptor);
        }

        boolean isConstructor() {
            return name.equals("<init>");
        }

        boolean isStaticInitializer() {
            return name.equals("<clinit>");
        }

        boolean isStatic() {
            return (flags & STATIC) != 0;
        }

        boolean isAbstract() {
            return (flags & ABSTRACT) != 0;
        }

        /** Compiler-made methods: bridges, lambdas and accessors are not something the author wrote. */
        boolean isSynthetic() {
            return (flags & (SYNTHETIC | VOLATILE_OR_BRIDGE)) != 0 && !isConstructor() || name.startsWith("lambda$");
        }

        boolean isLambdaBody() {
            return name.startsWith("lambda$");
        }

        String visibility() {
            return Visibility.of(flags).label();
        }

        /** The name with its parameter types: {@code pay(Money)}, which tells overloads apart. */
        String key() {
            return name + descriptor;
        }
    }

    /**
     * A call to a method.
     *
     * @param owner the binary name of the class the call names; the method may be declared in one of its super types
     */
    record Call(String owner, String name, String descriptor) {

        String key() {
            return name + descriptor;
        }
    }

    /** A component of a record. */
    record RecordComponentInfo(String name, String descriptor, String signature) {

        TypeRef type() {
            return Signatures.field(signature != null ? signature : descriptor);
        }
    }

    /**
     * An annotation that is kept in a class file.
     *
     * @param type the binary name of the annotation
     * @param values its attributes: text, numbers, booleans, {@link EnumValue}, {@link ClassValue}, a {@link List} of these, or a
     *     nested {@link AnnotationInfo}
     */
    record AnnotationInfo(String type, Map<String, Object> values) {

        AnnotationInfo {
            values = Map.copyOf(values);
        }

        /** The text of an attribute, or the given fallback. */
        String string(String attribute, String fallback) {
            return values.get(attribute) instanceof String text ? text : fallback;
        }

        /** The texts of an attribute that is a single text or an array of texts; empty if there is none. */
        List<String> strings(String attribute) {
            Object value = values.get(attribute);
            if (value instanceof String text) {
                return List.of(text);
            }
            if (value instanceof List<?> list) {
                return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
            }
            return List.of();
        }

        /** The class an attribute names, as binary name, or {@code null}. */
        String classValue(String attribute) {
            return values.get(attribute) instanceof ClassValue value ? value.name() : null;
        }
    }

    /** The value of an enum attribute of an annotation. */
    record EnumValue(String type, String name) {}

    /** The value of a class attribute of an annotation. */
    record ClassValue(String name) {}

    /** The visibility of a member, from least to most visible. */
    enum Visibility {
        PRIVATE("private"),
        PACKAGE("package"),
        PROTECTED("protected"),
        PUBLIC("public");

        private final String label;

        Visibility(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static Visibility of(int flags) {
            if ((flags & TypeInfo.PUBLIC) != 0) {
                return PUBLIC;
            }
            if ((flags & TypeInfo.PROTECTED) != 0) {
                return PROTECTED;
            }
            if ((flags & TypeInfo.PRIVATE) != 0) {
                return PRIVATE;
            }
            return PACKAGE;
        }
    }
}
