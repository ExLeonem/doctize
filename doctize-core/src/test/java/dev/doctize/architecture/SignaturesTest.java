package dev.doctize.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SignaturesTest {

    @Test
    void descriptorsAreSignaturesWithoutGenerics() {
        assertThat(Signatures.field("Ljava/lang/String;").display()).isEqualTo("String");
        assertThat(Signatures.field("[[I").display()).isEqualTo("int[][]");
        assertThat(Signatures.field("Z").isPrimitive()).isTrue();
        assertThat(Signatures.method("(ILjava/lang/String;[J)V").parameters()).extracting(TypeRef::display).containsExactly("int", "String", "long[]");
    }

    @Test
    void genericsAreNestedAndWildcardsBecomeTheirBound() {
        assertThat(Signatures.field("Ljava/util/Map<Ljava/lang/String;Ljava/util/List<+Lcom/acme/Order;>;>;").display())
                .isEqualTo("Map~String, List~Order~~");
        assertThat(Signatures.field("Ljava/util/List<*>;").display()).isEqualTo("List~Object~");
    }

    @Test
    void typeVariablesAreNotClassesAndAreNotCountedAsReferences() {
        TypeRef list = Signatures.field("Ljava/util/List<TT;>;");

        assertThat(list.display()).isEqualTo("List~T~");
        assertThat(list.arguments().get(0).variable()).isTrue();
        assertThat(list.classes()).containsExactly("java.util.List");
    }

    @Test
    void innerClassesOfGenericClassesKeepTheirNameAndTheirOwnArguments() {
        TypeRef entry = Signatures.field("Ljava/util/Map<TK;TV;>.Entry<Ljava/lang/String;>;");

        assertThat(entry.name()).isEqualTo("java.util.Map$Entry");
        assertThat(entry.display()).isEqualTo("Map.Entry~String~");
    }

    @Test
    void methodSignaturesWithTypeParametersBoundsAndThrowsAreRead() {
        Signatures.MethodSignature signature = Signatures.method("<T::Ljava/lang/Comparable<TT;>;>(Ljava/util/List<TT;>;)TT;^Ljava/io/IOException;");

        assertThat(signature.parameters()).extracting(TypeRef::display).containsExactly("List~T~");
        assertThat(signature.returnType().display()).isEqualTo("T");
        assertThat(signature.thrown()).extracting(TypeRef::display).containsExactly("IOException");
    }

    @Test
    void classSignaturesGiveTheTypeParametersWhateverTheirBounds() {
        Signatures.ClassSignature signature = Signatures.type(
                "<K:Ljava/lang/Object;V::Ljava/lang/Comparable<TV;>;:Ljava/io/Serializable;>Ljava/lang/Object;Ljava/util/function/Function<TK;TV;>;");

        assertThat(signature.typeParameters()).containsExactly("K", "V");
        assertThat(signature.superType().name()).isEqualTo("java.lang.Object");
        assertThat(signature.interfaces()).extracting(TypeRef::display).containsExactly("Function~K, V~");
    }

    @Test
    void nestedTypesAreShownAsOuterDotInner() {
        assertThat(TypeRef.displayName("com.acme.Order$Line")).isEqualTo("Order.Line");
        assertThat(TypeRef.of("com.acme.Order$Line").simpleName()).isEqualTo("Line");
    }

    @Test
    void textThatIsNoSignatureDoesNotThrow() {
        assertThat(Signatures.field("???").name()).isEqualTo("java.lang.Object");
        assertThat(Signatures.method("garbage").parameters()).isEmpty();
        assertThat(Signatures.type("<").typeParameters()).isEmpty();
    }
}
