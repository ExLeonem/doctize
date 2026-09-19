package dev.doctize.processor;

import dev.doctize.annotations.Doc;
import dev.doctize.annotations.DocIgnore;
import dev.doctize.annotations.DocPage;
import dev.doctize.model.Declaration;
import dev.doctize.model.ElementKind;
import dev.doctize.model.Parameter;
import dev.doctize.model.Placement;
import dev.doctize.model.Role;
import dev.doctize.model.SourceElement;
import dev.doctize.model.TypeNames;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

/** Reads annotated elements through {@code javax.lang.model} into the serializable {@link SourceElement} model. */
final class SourceElementFactory {

    private static final Set<String> OWN_ANNOTATIONS = Set.of(
            Doc.class.getName(), DocPage.class.getName(), DocIgnore.class.getName(), Override.class.getName());

    private final Elements elements;
    private final SignatureFormatter signatures = new SignatureFormatter();

    SourceElementFactory(Elements elements) {
        this.elements = elements;
    }

    SourceElement apiType(TypeElement type) {
        Doc doc = type.getAnnotation(Doc.class);
        List<SourceElement> members = doc.members() ? membersOf(type) : List.of();
        Placement placement = new Placement(doc.section(), doc.title(), doc.order(), doc.group());
        return new SourceElement(
                Role.API,
                declaration(type, typeKind(type)),
                comment(type),
                placement,
                Arrays.asList(doc.keywords()),
                members);
    }

    SourceElement page(Element element) {
        DocPage page = element.getAnnotation(DocPage.class);
        Placement placement = new Placement(page.section(), page.title(), page.order(), page.group());
        ElementKind kind = element instanceof TypeElement type ? typeKind(type) : ElementKind.PACKAGE;
        return new SourceElement(
                Role.PAGE, declaration(element, kind), comment(element), placement, Arrays.asList(page.keywords()), List.of());
    }

    private List<SourceElement> membersOf(TypeElement type) {
        return type.getEnclosedElements().stream()
                .filter(member -> elements.getOrigin(member) == Elements.Origin.EXPLICIT)
                .filter(member -> member.getAnnotation(DocIgnore.class) == null)
                .filter(SourceElementFactory::isVisible)
                .filter(member -> !isImplicit(type, member))
                .flatMap(member -> memberKind(member).stream().map(kind -> member(type, member, kind)))
                .toList();
    }

    private SourceElement member(TypeElement owner, Element member, ElementKind kind) {
        return new SourceElement(
                Role.API, declaration(member, kind, owner), comment(member), Placement.none(), List.of(), List.of());
    }

    private Declaration declaration(Element element, ElementKind kind) {
        return declaration(element, kind, null);
    }

    private Declaration declaration(Element element, ElementKind kind, TypeElement owner) {
        String name = kind == ElementKind.CONSTRUCTOR ? owner.getSimpleName().toString() : element.getSimpleName().toString();
        String packageName = elements.getPackageOf(element).getQualifiedName().toString();
        String qualifiedName = qualifiedName(element, owner, name);
        List<Parameter> parameters = element instanceof ExecutableElement method ? parametersOf(method) : List.of();
        String returnType = element instanceof ExecutableElement method && kind == ElementKind.METHOD
                ? TypeNames.simplify(method.getReturnType().toString())
                : null;
        List<String> thrown = element instanceof ExecutableElement method
                ? method.getThrownTypes().stream().map(t -> TypeNames.simplify(t.toString())).toList()
                : List.of();
        return new Declaration(
                kind,
                name,
                qualifiedName,
                packageName,
                signatures.of(element, kind, owner, elements),
                annotationsOf(element),
                parameters,
                returnType,
                thrown);
    }

    private static String qualifiedName(Element element, TypeElement owner, String name) {
        return switch (element) {
            case TypeElement type -> type.getQualifiedName().toString();
            case PackageElement pkg -> pkg.getQualifiedName().toString();
            default -> (owner == null ? "" : owner.getQualifiedName() + ".") + name;
        };
    }

    private List<Parameter> parametersOf(ExecutableElement method) {
        List<? extends VariableElement> declared = method.getParameters();
        return declared.stream()
                .map(parameter -> new Parameter(
                        parameter.getSimpleName().toString(), TypeNames.simplify(parameter.asType().toString())))
                .toList();
    }

    private static List<String> annotationsOf(Element element) {
        return element.getAnnotationMirrors().stream()
                .filter(mirror -> !OWN_ANNOTATIONS.contains(annotationName(mirror)))
                .map(mirror -> TypeNames.prettify(TypeNames.stripPackages(mirror.toString())))
                .toList();
    }

    private static String annotationName(AnnotationMirror mirror) {
        return ((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().toString();
    }

    private String comment(Element element) {
        String comment = elements.getDocComment(element);
        return comment == null ? "" : comment;
    }

    /**
     * The compiler adds members the author never wrote: {@code values()} and {@code valueOf(String)} of enums,
     * accessors and {@code equals}/{@code hashCode}/{@code toString} of records. They are only worth
     * documenting when the author gave them a comment.
     */
    private boolean isImplicit(TypeElement owner, Element member) {
        if (!comment(member).isBlank() || !(member instanceof ExecutableElement method)) {
            return false;
        }
        String name = method.getSimpleName().toString();
        return switch (owner.getKind()) {
            case ENUM -> name.equals("values") && method.getParameters().isEmpty()
                    || name.equals("valueOf") && method.getParameters().size() == 1;
            case RECORD -> Set.of("toString", "hashCode", "equals").contains(name)
                    || method.getParameters().isEmpty() && owner.getRecordComponents().stream()
                            .anyMatch(component -> component.getSimpleName().contentEquals(name));
            default -> false;
        };
    }

    /** Public and protected members are documented, and everything an interface declares. */
    private static boolean isVisible(Element member) {
        Set<Modifier> modifiers = member.getModifiers();
        return !modifiers.contains(Modifier.PRIVATE)
                && (modifiers.contains(Modifier.PUBLIC)
                        || modifiers.contains(Modifier.PROTECTED)
                        || member.getEnclosingElement().getKind().isInterface());
    }

    private static Optional<ElementKind> memberKind(Element member) {
        return switch (member.getKind()) {
            case FIELD -> Optional.of(ElementKind.FIELD);
            case ENUM_CONSTANT -> Optional.of(ElementKind.ENUM_CONSTANT);
            case METHOD -> Optional.of(ElementKind.METHOD);
            case CONSTRUCTOR -> Optional.of(ElementKind.CONSTRUCTOR);
            case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> Optional.of(typeKind((TypeElement) member));
            default -> Optional.empty();
        };
    }

    private static ElementKind typeKind(TypeElement type) {
        return switch (type.getKind()) {
            case INTERFACE -> ElementKind.INTERFACE;
            case ENUM -> ElementKind.ENUM;
            case RECORD -> ElementKind.RECORD;
            case ANNOTATION_TYPE -> ElementKind.ANNOTATION_TYPE;
            default -> ElementKind.CLASS;
        };
    }
}
