package dev.doctize.architecture;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the type descriptors and generic signatures of class files (JVMS 4.3 and 4.7.9.1) into {@link TypeRef}s.
 * A descriptor is a signature without generics, so one reader serves both.
 */
final class Signatures {

    /** The parameters, the return type and the exceptions of a method. */
    record MethodSignature(List<TypeRef> parameters, TypeRef returnType, List<TypeRef> thrown) {}

    /** The type parameters of a class, e.g. {@code [K, V]} for {@code Cache<K, V>}, and the supertypes with their arguments. */
    record ClassSignature(List<String> typeParameters, TypeRef superType, List<TypeRef> interfaces) {}

    private final String text;
    private int position;

    private Signatures(String text) {
        this.text = text;
    }

    /** The type of a field, from its signature or descriptor. Unreadable text gives {@code Object}. */
    static TypeRef field(String signature) {
        try {
            return new Signatures(signature).type();
        } catch (RuntimeException e) {
            return TypeRef.of("java.lang.Object");
        }
    }

    /** A method, from its signature or descriptor. Unreadable text gives an empty method. */
    static MethodSignature method(String signature) {
        try {
            Signatures reader = new Signatures(signature);
            reader.typeParameters();
            reader.expect('(');
            List<TypeRef> parameters = new ArrayList<>();
            while (reader.peek() != ')') {
                parameters.add(reader.type());
            }
            reader.expect(')');
            TypeRef result = reader.type();
            List<TypeRef> thrown = new ArrayList<>();
            while (reader.position < reader.text.length() && reader.peek() == '^') {
                reader.position++;
                thrown.add(reader.type());
            }
            return new MethodSignature(parameters, result, thrown);
        } catch (RuntimeException e) {
            return new MethodSignature(List.of(), TypeRef.VOID, List.of());
        }
    }

    /** A class signature: its type parameters, super class and interfaces. Unreadable text gives none of them. */
    static ClassSignature type(String signature) {
        try {
            Signatures reader = new Signatures(signature);
            List<String> parameters = reader.typeParameters();
            TypeRef superType = reader.type();
            List<TypeRef> interfaces = new ArrayList<>();
            while (reader.position < reader.text.length()) {
                interfaces.add(reader.type());
            }
            return new ClassSignature(parameters, superType, interfaces);
        } catch (RuntimeException e) {
            return new ClassSignature(List.of(), TypeRef.of("java.lang.Object"), List.of());
        }
    }

    private char peek() {
        return text.charAt(position);
    }

    private void expect(char c) {
        if (text.charAt(position++) != c) {
            throw new IllegalArgumentException("expected " + c + " at " + (position - 1) + " in " + text);
        }
    }

    /**
     * {@code <K:Ljava/lang/Object;V::Ljava/lang/Comparable<TV;>;>}. Each parameter is a name, a colon, an optional class bound and
     * any number of interface bounds, each after a colon. The names are kept and the bounds skipped.
     */
    private List<String> typeParameters() {
        List<String> names = new ArrayList<>();
        if (position >= text.length() || peek() != '<') {
            return names;
        }
        position++;
        while (peek() != '>') {
            int colon = text.indexOf(':', position);
            names.add(text.substring(position, colon));
            position = colon + 1;
            if (peek() != ':') {
                type();
            }
            while (peek() == ':') {
                position++;
                type();
            }
        }
        position++;
        return names;
    }

    private TypeRef type() {
        int arrays = 0;
        while (peek() == '[') {
            arrays++;
            position++;
        }
        char c = text.charAt(position);
        TypeRef base;
        switch (c) {
            case 'B' -> base = primitive("byte");
            case 'C' -> base = primitive("char");
            case 'D' -> base = primitive("double");
            case 'F' -> base = primitive("float");
            case 'I' -> base = primitive("int");
            case 'J' -> base = primitive("long");
            case 'S' -> base = primitive("short");
            case 'Z' -> base = primitive("boolean");
            case 'V' -> base = primitive("void");
            case 'T' -> {
                int end = text.indexOf(';', position);
                base = new TypeRef(text.substring(position + 1, end), List.of(), 0, true);
                position = end + 1;
            }
            case 'L' -> base = classType();
            default -> throw new IllegalArgumentException("unexpected " + c + " at " + position + " in " + text);
        }
        return arrays == 0 ? base : new TypeRef(base.name(), base.arguments(), arrays, base.variable());
    }

    private TypeRef primitive(String name) {
        position++;
        return TypeRef.of(name);
    }

    /** {@code Ljava/util/Map<TK;Ljava/util/List<Lcom/acme/Order;>;>.Entry<...>;} */
    private TypeRef classType() {
        position++;
        StringBuilder name = new StringBuilder();
        List<TypeRef> arguments = new ArrayList<>();
        while (true) {
            char c = text.charAt(position);
            if (c == ';') {
                position++;
                return new TypeRef(name.toString(), arguments, 0, false);
            } else if (c == '<') {
                position++;
                // The arguments of an outer class are dropped when an inner class follows: only the last ones are kept.
                arguments = new ArrayList<>();
                while (peek() != '>') {
                    arguments.add(argument());
                }
                position++;
            } else if (c == '.') {
                position++;
                name.append('$');
                arguments = new ArrayList<>();
            } else {
                name.append(c == '/' ? '.' : c);
                position++;
            }
        }
    }

    /** A type argument; a wildcard is written as its bound, and an unbounded one as {@code Object}. */
    private TypeRef argument() {
        char c = peek();
        if (c == '*') {
            position++;
            return TypeRef.of("java.lang.Object");
        }
        if (c == '+' || c == '-') {
            position++;
        }
        return type();
    }
}
