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

    private static List<String> attributeNames(Class<?> annotation) {
        return Arrays.stream(annotation.getDeclaredMethods()).map(Method::getName).toList();
    }
}
