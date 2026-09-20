package dev.doctize.architecture;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A type as written in a signature: a class with type arguments, a primitive, a type variable, or an array of these.
 *
 * @param name the binary name of a class ({@code java.util.List}, {@code com.acme.Order$Line}), the keyword of a primitive
 *     ({@code int}), {@code void}, or the name of a type variable ({@code T})
 * @param arguments the type arguments, e.g. {@code Order} for {@code List<Order>}
 * @param arrays how many array dimensions there are
 * @param variable whether {@code name} is a type variable
 */
record TypeRef(String name, List<TypeRef> arguments, int arrays, boolean variable) {

    static final TypeRef VOID = new TypeRef("void", List.of(), 0, false);

    TypeRef {
        arguments = List.copyOf(arguments);
    }

    static TypeRef of(String name) {
        return new TypeRef(name, List.of(), 0, false);
    }

    boolean isPrimitive() {
        return arrays == 0 && !variable && switch (name) {
            case "boolean", "byte", "char", "short", "int", "long", "float", "double", "void" -> true;
            default -> false;
        };
    }

    boolean isArray() {
        return arrays > 0;
    }

    /** The type without its array dimensions: {@code Order} for {@code Order[]}. */
    TypeRef element() {
        return arrays == 0 ? this : new TypeRef(name, arguments, 0, variable);
    }

    /** The class name without package and without the classes it is nested in: {@code Line} for {@code com.acme.Order$Line}. */
    String simpleName() {
        return simple(name);
    }

    /** As Mermaid writes generic types: {@code Map~String, List~Order~~}. Nested types are written {@code Outer.Inner}. */
    String display() {
        StringBuilder out = new StringBuilder(displayName(name));
        if (!arguments.isEmpty()) {
            out.append('~').append(arguments.stream().map(TypeRef::display).collect(Collectors.joining(", "))).append('~');
        }
        return out.append("[]".repeat(arrays)).toString();
    }

    /** Every class this type mentions, including its type arguments. */
    List<String> classes() {
        java.util.ArrayList<String> classes = new java.util.ArrayList<>();
        if (!variable && !isPrimitiveName(name)) {
            classes.add(name);
        }
        arguments.forEach(argument -> classes.addAll(argument.classes()));
        return classes;
    }

    static String simple(String binaryName) {
        String afterPackage = binaryName.substring(binaryName.lastIndexOf('.') + 1);
        return afterPackage.substring(afterPackage.lastIndexOf('$') + 1);
    }

    /** {@code Order.Line} for {@code com.acme.Order$Line}, {@code Order} for {@code com.acme.Order}. */
    static String displayName(String binaryName) {
        return binaryName.substring(binaryName.lastIndexOf('.') + 1).replace('$', '.');
    }

    private static boolean isPrimitiveName(String name) {
        return switch (name) {
            case "boolean", "byte", "char", "short", "int", "long", "float", "double", "void" -> true;
            default -> false;
        };
    }
}
