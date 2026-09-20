package dev.doctize.annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The annotations are the public contract of documented code: their names, defaults and reach must not drift. */
class AnnotationContractTest {

    private static Object defaultOf(Class<?> annotation, String attribute) throws NoSuchMethodException {
        return annotation.getMethod(attribute).getDefaultValue();
    }

    @ParameterizedTest
    @ValueSource(classes = {Doc.class, DocPage.class, DocIgnore.class})
    void haveSourceRetentionSoTheyLeaveNoTraceInClassFiles(Class<?> annotation) {
        assertThat(annotation.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.SOURCE);
        assertThat(annotation.isAnnotation()).isTrue();
        assertThat(annotation.isAnnotationPresent(Documented.class)).isTrue();
    }

    @Test
    void docAppliesToTypesOnly() {
        assertThat(Doc.class.getAnnotation(Target.class).value()).containsExactly(ElementType.TYPE);
    }

    @Test
    void docPageAppliesToPackagesAndTypes() {
        assertThat(DocPage.class.getAnnotation(Target.class).value()).containsExactlyInAnyOrder(ElementType.PACKAGE, ElementType.TYPE);
    }

    @Test
    void docIgnoreAppliesToMembersAndTypes() {
        assertThat(DocIgnore.class.getAnnotation(Target.class).value())
                .containsExactlyInAnyOrder(ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.TYPE);
        assertThat(DocIgnore.class.getDeclaredMethods()).isEmpty();
    }

    @Test
    void docHasTheDocumentedAttributesWithSafeDefaults() throws Exception {
        assertThat(attributeNames(Doc.class)).containsExactlyInAnyOrder("section", "title", "order", "group", "keywords", "members");
        assertThat(defaultOf(Doc.class, "section")).isEqualTo("");
        assertThat(defaultOf(Doc.class, "title")).isEqualTo("");
        assertThat(defaultOf(Doc.class, "group")).isEqualTo("");
        assertThat(defaultOf(Doc.class, "order")).isEqualTo(100);
        assertThat((String[]) defaultOf(Doc.class, "keywords")).isEmpty();
        assertThat(defaultOf(Doc.class, "members")).isEqualTo(true);
    }

    @Test
    void docPageHasThePlacementAttributesOfDocButNoMemberSwitch() throws Exception {
        assertThat(attributeNames(DocPage.class)).containsExactlyInAnyOrder("section", "title", "order", "group", "keywords");
        assertThat(defaultOf(DocPage.class, "order")).isEqualTo(100);
    }

    @Test
    void theDefaultOrderMatchesTheOneTheGeneratorAssumes() throws Exception {
        // Placement.DEFAULT_ORDER lives in doctize-core, which this module must not depend on; the value is documented instead.
        assertThat(defaultOf(Doc.class, "order")).isEqualTo(100);
        assertThat(defaultOf(DocPage.class, "order")).isEqualTo(100);
    }

    private static final List<Class<? extends java.lang.annotation.Annotation>> DIAGRAM_ANNOTATIONS =
            List.of(DiagramIgnore.class, DiagramInclude.class, StateMachine.class, Transition.class, Transitions.class, Flow.class);

    @Test
    void theDiagramAnnotationsAreKeptInTheClassFileBecauseTheDiagramsAreMadeFromClassFiles() {
        for (Class<? extends java.lang.annotation.Annotation> annotation : DIAGRAM_ANNOTATIONS) {
            assertThat(annotation.getAnnotation(Retention.class).value()).as(annotation.getSimpleName()).isEqualTo(RetentionPolicy.CLASS);
            assertThat(annotation.isAnnotationPresent(Documented.class)).as(annotation.getSimpleName()).isTrue();
        }
    }

    @Test
    void ignoreAndIncludeMarkTypesMembersAndPackagesAndCanBeLimitedToSomeDiagrams() throws Exception {
        for (Class<?> annotation : List.of(DiagramIgnore.class, DiagramInclude.class)) {
            assertThat(annotation.getAnnotation(Target.class).value()).as(annotation.getSimpleName())
                    .containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE);
            assertThat(attributeNames(annotation)).containsExactly("types");
            assertThat((String[]) defaultOf(annotation, "types")).as("no types means all diagrams").isEmpty();
        }
    }

    @Test
    void stateMachinesAreEnumsAndTransitionsAreRepeatableOnConstantsAndMethods() throws Exception {
        assertThat(StateMachine.class.getAnnotation(Target.class).value()).containsExactly(ElementType.TYPE);
        assertThat(attributeNames(StateMachine.class)).containsExactlyInAnyOrder("name", "initial", "terminal", "description");
        assertThat(Transition.class.getAnnotation(Target.class).value()).containsExactlyInAnyOrder(ElementType.FIELD, ElementType.METHOD);
        assertThat(attributeNames(Transition.class)).containsExactlyInAnyOrder("from", "to", "on", "machine");
        assertThat(Transition.class.getAnnotation(java.lang.annotation.Repeatable.class).value()).isEqualTo(Transitions.class);
        assertThat(Transitions.class.getMethod("value").getReturnType()).isEqualTo(Transition[].class);
        assertThat(defaultOf(Transition.class, "machine")).isEqualTo(Void.class);
        assertThat(Transition.class.getMethod("to").getDefaultValue()).as("the target state is required").isNull();
    }

    @Test
    void aFlowStartsAtAMethod() throws Exception {
        assertThat(Flow.class.getAnnotation(Target.class).value()).containsExactly(ElementType.METHOD);
        assertThat(attributeNames(Flow.class)).containsExactlyInAnyOrder("name", "description");
    }

    @Test
    void theDiagramTypesAreTextSoThatAnnotatedCodeNeverMentionsAnEnumOfThisLibrary() throws Exception {
        assertThat(DiagramTypes.OVERVIEW).isEqualTo("overview");
        assertThat(DiagramTypes.CLASSES).isEqualTo("classes");
        assertThat(DiagramTypes.STATES).isEqualTo("states");
        assertThat(DiagramTypes.ENTITIES).isEqualTo("entities");
        assertThat(DiagramTypes.FLOWS).isEqualTo("flows");
        for (Class<?> annotation : List.of(DiagramIgnore.class, DiagramInclude.class)) {
            assertThat(annotation.getMethod("types").getReturnType()).as("an enum attribute makes users of a library see compiler warnings")
                    .isEqualTo(String[].class);
        }
        for (Class<? extends java.lang.annotation.Annotation> annotation : DIAGRAM_ANNOTATIONS) {
            assertThat(attributeTypes(annotation)).as(annotation.getSimpleName()).noneMatch(Class::isEnum);
        }
    }

    private static List<Class<?>> attributeTypes(Class<?> annotation) {
        return Arrays.stream(annotation.getDeclaredMethods()).<Class<?>>map(Method::getReturnType).map(type -> type.isArray() ? type.getComponentType() : type).toList();
    }

    private static List<String> attributeNames(Class<?> annotation) {
        return Arrays.stream(annotation.getDeclaredMethods()).map(Method::getName).toList();
    }
}
