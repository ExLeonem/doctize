package dev.doctize.processor;

import dev.doctize.model.ElementKind;
import dev.doctize.model.TypeNames;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** Formats declarations as Java source text with simple type names, e.g. {@code public <T> List<T> find(String name)}. */
final class SignatureFormatter {

    String of(Element element, ElementKind kind, TypeElement owner, Elements elements) {
        return switch (kind) {
            case METHOD -> method((ExecutableElement) element, owner);
            case CONSTRUCTOR -> constructor((ExecutableElement) element);
            case FIELD -> field((VariableElement) element, elements);
            case ENUM_CONSTANT -> element.getSimpleName().toString();
            case PACKAGE -> "package " + element;
            default -> type((TypeElement) element);
        };
    }

    private String type(TypeElement type) {
        StringBuilder out = new StringBuilder(modifiers(type, hiddenForType(type)));
        out.append(keyword(type)).append(' ').append(type.getSimpleName()).append(typeParameters(type.getTypeParameters()));
        if (type.getKind() == javax.lang.model.element.ElementKind.RECORD) {
            out.append(type.getRecordComponents().stream()
                    .map(SignatureFormatter::component)
                    .collect(Collectors.joining(", ", "(", ")")));
        }
        boolean isInterface = type.getKind().isInterface();
        TypeMirror superclass = type.getSuperclass();
        if (type.getKind() == javax.lang.model.element.ElementKind.CLASS && !isObject(superclass)) {
            out.append(" extends ").append(TypeNames.simplify(superclass.toString()));
        }
        if (type.getKind() != javax.lang.model.element.ElementKind.ANNOTATION_TYPE) {
            appendList(out, isInterface ? " extends " : " implements ", type.getInterfaces());
        }
        appendList(out, " permits ", type.getPermittedSubclasses());
        return out.toString();
    }

    private static String component(RecordComponentElement component) {
        return TypeNames.simplify(component.asType().toString()) + " " + component.getSimpleName();
    }

    private String method(ExecutableElement method, TypeElement owner) {
        StringBuilder out = new StringBuilder(modifiers(method, hiddenForMember(owner)));
        String typeParameters = typeParameters(method.getTypeParameters());
        if (!typeParameters.isEmpty()) {
            out.append(typeParameters).append(' ');
        }
        out.append(TypeNames.simplify(method.getReturnType().toString())).append(' ').append(method.getSimpleName());
        out.append(parameters(method));
        appendList(out, " throws ", method.getThrownTypes());
        if (method.getDefaultValue() != null) {
            out.append(" default ").append(TypeNames.stripPackages(method.getDefaultValue().toString()));
        }
        return out.toString();
    }

    private String constructor(ExecutableElement constructor) {
        String typeParameters = typeParameters(constructor.getTypeParameters());
        StringBuilder out = new StringBuilder(modifiers(constructor, Set.of()));
        if (!typeParameters.isEmpty()) {
            out.append(typeParameters).append(' ');
        }
        out.append(constructor.getEnclosingElement().getSimpleName()).append(parameters(constructor));
        appendList(out, " throws ", constructor.getThrownTypes());
        return out.toString();
    }

    private String field(VariableElement field, Elements elements) {
        Set<Modifier> hidden = field.getEnclosingElement().getKind().isInterface()
                ? Set.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                : Set.of();
        StringBuilder out = new StringBuilder(modifiers(field, hidden));
        out.append(TypeNames.simplify(field.asType().toString())).append(' ').append(field.getSimpleName());
        if (field.getConstantValue() != null) {
            out.append(" = ").append(elements.getConstantExpression(field.getConstantValue()));
        }
        return out.toString();
    }

    private static String parameters(ExecutableElement executable) {
        List<? extends VariableElement> parameters = executable.getParameters();
        return IntStream.range(0, parameters.size())
                .mapToObj(i -> parameter(parameters.get(i), executable.isVarArgs() && i == parameters.size() - 1))
                .collect(Collectors.joining(", ", "(", ")"));
    }

    private static String parameter(VariableElement parameter, boolean varArgs) {
        String type = TypeNames.simplify(parameter.asType().toString());
        String shown = varArgs && type.endsWith("[]") ? type.substring(0, type.length() - 2) + "..." : type;
        return shown + " " + parameter.getSimpleName();
    }

    private static String typeParameters(List<? extends TypeParameterElement> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }
        return parameters.stream().map(SignatureFormatter::typeParameter).collect(Collectors.joining(", ", "<", ">"));
    }

    private static String typeParameter(TypeParameterElement parameter) {
        List<String> bounds = parameter.getBounds().stream()
                .filter(bound -> !isObject(bound))
                .map(bound -> TypeNames.simplify(bound.toString()))
                .toList();
        return bounds.isEmpty() ? parameter.getSimpleName().toString() : parameter.getSimpleName() + " extends " + String.join(" & ", bounds);
    }

    private static void appendList(StringBuilder out, String keyword, List<? extends TypeMirror> types) {
        if (!types.isEmpty()) {
            out.append(keyword).append(types.stream().map(t -> TypeNames.simplify(t.toString())).collect(Collectors.joining(", ")));
        }
    }

    private static String modifiers(Element element, Set<Modifier> hidden) {
        String text = element.getModifiers().stream()
                .filter(modifier -> !hidden.contains(modifier))
                .map(Modifier::toString)
                .collect(Collectors.joining(" "));
        return text.isEmpty() ? "" : text + " ";
    }

    /** Modifiers that every interface member has implicitly. */
    private static Set<Modifier> hiddenForMember(TypeElement owner) {
        return owner != null && owner.getKind().isInterface() ? Set.of(Modifier.PUBLIC, Modifier.ABSTRACT) : Set.of();
    }

    /** Modifiers that are implied by the kind of type. */
    private static Set<Modifier> hiddenForType(TypeElement type) {
        return switch (type.getKind()) {
            case INTERFACE, ANNOTATION_TYPE -> Set.of(Modifier.ABSTRACT, Modifier.STATIC);
            case ENUM, RECORD -> Set.of(Modifier.FINAL, Modifier.STATIC);
            default -> Set.of();
        };
    }

    private static String keyword(TypeElement type) {
        return switch (type.getKind()) {
            case INTERFACE -> "interface";
            case ANNOTATION_TYPE -> "@interface";
            case ENUM -> "enum";
            case RECORD -> "record";
            default -> "class";
        };
    }

    private static boolean isObject(TypeMirror type) {
        return type.toString().equals("java.lang.Object") || type.getKind() == TypeKind.NONE;
    }
}
