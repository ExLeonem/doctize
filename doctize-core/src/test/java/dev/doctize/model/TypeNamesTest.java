package dev.doctize.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TypeNamesTest {

    @Test
    void simplifyRemovesPackagesAndAnnotationsAndAddsSpaces() {
        assertThat(TypeNames.simplify("java.util.Map<java.lang.String,java.util.List<java.lang.@org.x.Nullable Integer>>"))
                .isEqualTo("Map<String, List<Integer>>");
        assertThat(TypeNames.simplify("java.util.Map.Entry<K,V>")).isEqualTo("Map.Entry<K, V>");
        assertThat(TypeNames.simplify("int[]")).isEqualTo("int[]");
    }

    @Test
    void erasureDropsGenerics() {
        assertThat(TypeNames.erasure("java.util.Map<K, java.util.List<V>>")).isEqualTo("Map");
        assertThat(TypeNames.erasure("java.util.List<String>[]")).isEqualTo("List[]");
    }

    @Test
    void prettifyFormatsAnnotationValues() {
        assertThat(TypeNames.prettify("@Deprecated(since=\"1\",forRemoval=true)"))
                .isEqualTo("@Deprecated(since = \"1\", forRemoval = true)");
    }
}
