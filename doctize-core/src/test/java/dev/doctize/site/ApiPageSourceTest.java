package dev.doctize.site;

import static dev.doctize.Fixtures.member;
import static dev.doctize.Fixtures.method;
import static dev.doctize.Fixtures.type;
import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.config.ApiSettings;
import dev.doctize.model.Declaration;
import dev.doctize.model.ElementKind;
import dev.doctize.model.Placement;
import dev.doctize.model.Role;
import dev.doctize.model.SourceElement;
import dev.doctize.model.SourceModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiPageSourceTest {

    private static List<Page> pages(ApiSettings settings, SourceElement... elements) {
        return new ApiPageSource(List.of(SourceModel.of(List.of(elements))), settings).pages();
    }

    @Test
    void anApiTypeBecomesAPageInTheApiSectionGroupedByPackage() {
        Page page = pages(ApiSettings.defaults(), type("com.acme.Cache", "A cache.")).getFirst();

        assertThat(page.slug()).isEqualTo("com.acme.Cache");
        assertThat(page.title()).isEqualTo("Cache");
        assertThat(page.section()).isEqualTo("API Reference");
        assertThat(page.group()).isEqualTo("com.acme");
        assertThat(page.order()).isEqualTo(Placement.DEFAULT_ORDER);
        assertThat(page.content()).isInstanceOf(PageContent.Type.class);
    }

    @Test
    void placementFromTheAnnotationWins() {
        SourceElement element = type(ElementKind.CLASS, "com.acme.Cache", "A.", new Placement("Caching", "The Cache", 3, "Core"));

        Page page = pages(new ApiSettings("Elsewhere", true), element).getFirst();

        assertThat(page).extracting(Page::section, Page::title, Page::group, Page::order).containsExactly("Caching", "The Cache", "Core", 3);
    }

    @Test
    void theConfiguredApiSectionIsTheDefault() {
        assertThat(pages(new ApiSettings("Reference", true), type("a.T", "")).getFirst().section()).isEqualTo("Reference");
    }

    @Test
    void undocumentedMembersCanBeLeftOut() {
        SourceElement element = type("a.T", "Doc.", method("documented", "Yes.", "void"), method("bare", "", "void"), member(ElementKind.FIELD, "F", "int F", "  "));

        List<SourceElement> all = members(pages(ApiSettings.defaults(), element));
        List<SourceElement> documentedOnly = members(pages(new ApiSettings("API Reference", false), element));

        assertThat(all).hasSize(3);
        assertThat(documentedOnly).extracting(m -> m.declaration().name()).containsExactly("documented");
    }

    private static List<SourceElement> members(List<Page> pages) {
        return ((PageContent.Type) pages.getFirst().content()).element().members();
    }

    @Test
    void aProsePageTakesItsTitleFromTheAnnotationThenTheLeadingHeadingThenTheName() {
        Declaration pkg = new Declaration(ElementKind.PACKAGE, "cache", "com.acme.cache", "com.acme.cache", "package com.acme.cache", List.of(), List.of(), null, List.of());
        SourceElement withHeading = new SourceElement(Role.PAGE, pkg, "# Heading Title\n\nBody text.\n\n@since 1", new Placement("Guides", "", 10, ""), List.of("kw"), List.of());
        SourceElement withTitle = new SourceElement(Role.PAGE, pkg, "# Heading Title\n\nBody.", new Placement("Guides", "Annotated Title", 10, ""), List.of(), List.of());
        SourceElement bare = new SourceElement(Role.PAGE, pkg, "Just text.", Placement.none(), List.of(), List.of());

        List<Page> pages = pages(ApiSettings.defaults(), withHeading, withTitle, bare);

        assertThat(pages).extracting(Page::title).containsExactly("Heading Title", "Annotated Title", "Cache");
        assertThat(pages.getFirst().slug()).isEqualTo("heading-title");
        assertThat(pages.getFirst().keywords()).containsExactly("kw");
        assertThat(pages.getFirst().content()).isEqualTo(new PageContent.Guide("Body text.", ""));
        assertThat(pages.get(2).section()).as("undeclared: the assembler applies the default section").isEmpty();
    }

    @Test
    void severalModelsAreCombinedInOrder() {
        ApiPageSource source = new ApiPageSource(List.of(SourceModel.of(List.of(type("a.One", ""))), SourceModel.of(List.of(type("b.Two", "")))), ApiSettings.defaults());

        assertThat(source.pages()).extracting(Page::title).containsExactly("One", "Two");
    }
}
