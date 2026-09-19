package dev.doctize.render;

import static dev.doctize.Fixtures.member;
import static dev.doctize.Fixtures.method;
import static dev.doctize.Fixtures.param;
import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.model.ElementKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnchorsAndLabelsTest {

    @Test
    void anchorsNameTheKindOfMemberAndTheErasedParameterTypes() {
        assertThat(Anchors.of(method("get", "", "V", param("k", "K")).declaration())).isEqualTo("method-get-K");
        assertThat(Anchors.of(method("size", "", "int").declaration())).isEqualTo("method-size");
        assertThat(Anchors.of(method("put", "", "void", param("m", "java.util.Map<String, List<X>>"), param("a", "int[]"), param("v", "String...")).declaration()))
                .isEqualTo("method-put-Map-intArray-StringArray");
        assertThat(Anchors.of(member(ElementKind.FIELD, "MAX", "int MAX", "").declaration())).isEqualTo("field-MAX");
        assertThat(Anchors.of(member(ElementKind.ENUM_CONSTANT, "RED", "RED", "").declaration())).isEqualTo("constant-RED");
        assertThat(Anchors.of(member(ElementKind.CONSTRUCTOR, "Cache", "Cache()", "").declaration())).isEqualTo("constructor-Cache");
        assertThat(Anchors.of(member(ElementKind.CLASS, "Inner", "class Inner", "").declaration())).isEqualTo("type-Inner");
    }

    @Test
    void anOverloadHasItsOwnAnchor() {
        String one = Anchors.of(method("add", "", "void", param("a", "int")).declaration());
        String two = Anchors.of(method("add", "", "void", param("a", "int"), param("b", "int")).declaration());

        assertThat(one).isNotEqualTo(two);
    }

    @ParameterizedTest
    @CsvSource({"since,Since", "see,See also", "apiNote,API note", "implNote,Implementation note", "author,Author", "myCustomTag,My custom tag", "kebab-case-tag,Kebab case tag", "x,X"})
    void tagLabelsAreKnownOrDerivedFromTheName(String tag, String label) {
        assertThat(TagLabels.labelFor(tag)).isEqualTo(label);
    }

    @ParameterizedTest
    @CsvSource({"String,true", "Object,true", "java.util.List,true", "List#of(),true", "Duration#ZERO,true", "#size,false", "com.acme.Nope,false", "Nope#x,false", "shop.Cart,false"})
    void jdkTypesAreRecognisedSoTheirLinksAreNotReportedAsBroken(String reference, boolean known) {
        assertThat(JdkTypes.isKnown(reference)).isEqualTo(known);
    }
}
