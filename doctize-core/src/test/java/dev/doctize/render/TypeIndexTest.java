package dev.doctize.render;

import static dev.doctize.Fixtures.apiPage;
import static dev.doctize.Fixtures.guide;
import static dev.doctize.Fixtures.member;
import static dev.doctize.Fixtures.method;
import static dev.doctize.Fixtures.param;
import static dev.doctize.Fixtures.site;
import static dev.doctize.Fixtures.type;
import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.model.ElementKind;
import dev.doctize.model.Placement;
import dev.doctize.model.SourceElement;
import dev.doctize.site.Site;
import dev.doctize.site.SitePage;
import java.util.List;
import org.junit.jupiter.api.Test;

class TypeIndexTest {

    SourceElement cache = type("com.acme.Cache", "A cache.",
            method("get", "", "V", param("key", "K")),
            method("get", "", "V", param("key", "K"), param("fallback", "V")),
            method("put", "", "void", param("key", "K"), param("value", "V")),
            method("load", "", "V", param("names", "List<String>")),
            member(ElementKind.FIELD, "SIZE", "int SIZE", ""),
            type(ElementKind.CLASS, "com.acme.Cache.Entry", "An entry.", Placement.none()));
    SourceElement entry = type(ElementKind.CLASS, "com.acme.Cache.Entry", "An entry.", Placement.none(), method("key", "", "K"));
    SourceElement other = type("org.other.Store", "A store.");

    Site site = site(apiPage(cache), apiPage(other), guide("start", "Start", "guides", "text", "start.md"));

    private SitePage pageOf(String typeName) {
        return site.pages().stream().filter(p -> p.page().slug().equals(typeName)).findFirst().orElseThrow();
    }

    private String resolve(String reference) {
        return new TypeIndex(site).resolve(reference, pageOf("com.acme.Cache")).orElse("(unresolved)");
    }

    @Test
    void resolvesTypesBySimpleAndQualifiedName() {
        assertThat(resolve("Cache")).isEqualTo("api/com.acme.Cache.html");
        assertThat(resolve("com.acme.Cache")).isEqualTo("api/com.acme.Cache.html");
        assertThat(resolve("Store")).isEqualTo("api/org.other.Store.html");
    }

    @Test
    void resolvesMembersByNameWithOrWithoutParameters() {
        assertThat(resolve("Cache#put")).isEqualTo("api/com.acme.Cache.html#method-put-K-V");
        assertThat(resolve("Cache#put(Object, Object)")).isEqualTo("api/com.acme.Cache.html#method-put-K-V");
        assertThat(resolve("Cache#get(Object)")).isEqualTo("api/com.acme.Cache.html#method-get-K");
        assertThat(resolve("Cache#get(Object, Object)")).isEqualTo("api/com.acme.Cache.html#method-get-K-V");
        assertThat(resolve("Cache#load(List)")).isEqualTo("api/com.acme.Cache.html#method-load-List");
        assertThat(resolve("Cache#SIZE")).isEqualTo("api/com.acme.Cache.html#field-SIZE");
    }

    @Test
    void anUnqualifiedNameOfAnOverloadedMethodResolvesToTheFirstDeclaration() {
        assertThat(resolve("Cache#get")).isEqualTo("api/com.acme.Cache.html#method-get-K");
    }

    @Test
    void aReferenceWithoutTypeMeansTheTypeOfTheCurrentPage() {
        assertThat(resolve("#put")).isEqualTo("api/com.acme.Cache.html#method-put-K-V");
        assertThat(new TypeIndex(site).resolve("#put", pageOf("start"))).as("a guide has no current type").isEmpty();
    }

    @Test
    void aWrongParameterListDoesNotResolve() {
        assertThat(resolve("Cache#put(String)")).isEqualTo("(unresolved)");
        assertThat(resolve("Cache#get(int, int, int)")).isEqualTo("(unresolved)");
    }

    @Test
    void unknownTypesAndMembersDoNotResolve() {
        assertThat(resolve("Missing")).isEqualTo("(unresolved)");
        assertThat(resolve("Cache#missing")).isEqualTo("(unresolved)");
        assertThat(resolve("java.util.List")).isEqualTo("(unresolved)");
        assertThat(resolve("")).isEqualTo("api/com.acme.Cache.html");
    }

    @Test
    void aNestedTypeIsReachedThroughItsOwnerWhenItHasNoPageOfItsOwn() {
        assertThat(resolve("Cache.Entry")).isEqualTo("api/com.acme.Cache.html#type-Entry");
        assertThat(resolve("com.acme.Cache.Entry")).isEqualTo("api/com.acme.Cache.html#type-Entry");
    }

    @Test
    void aNestedTypeWithItsOwnPageIsReachedThroughThatPage() {
        site = site(apiPage(cache), apiPage(entry));

        assertThat(resolve("Cache.Entry")).isEqualTo("api/com.acme.Cache.Entry.html");
        assertThat(resolve("Entry#key")).isEqualTo("api/com.acme.Cache.Entry.html#method-key");
        assertThat(resolve("com.acme.Cache.Entry")).isEqualTo("api/com.acme.Cache.Entry.html");
    }

    @Test
    void typeUrlIsForLinkingNamesInSignatures() {
        TypeIndex index = new TypeIndex(site);

        assertThat(index.typeUrl("Cache")).contains("api/com.acme.Cache.html");
        assertThat(index.typeUrl("String")).isEmpty();
        assertThat(List.of(index.typeUrl("Store"))).isNotEmpty();
    }
}
