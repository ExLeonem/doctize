package dev.doctize.render;

import static dev.doctize.Fixtures.member;
import static dev.doctize.Fixtures.method;
import static dev.doctize.Fixtures.param;
import static dev.doctize.Fixtures.type;
import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.javadoc.InlineTagProcessor;
import dev.doctize.markdown.CodeHighlighter;
import dev.doctize.markdown.LinkRewriter;
import dev.doctize.markdown.MarkdownRenderer;
import dev.doctize.markdown.TextChunk;
import dev.doctize.markdown.TocEntry;
import dev.doctize.model.ElementKind;
import dev.doctize.model.SourceElement;
import dev.doctize.render.Views.MemberGroupView;
import dev.doctize.render.Views.MemberView;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiTypeViewFactoryTest {

    CodeHighlighter highlighter = new CodeHighlighter();
    Fragments fragments = new Fragments(new MarkdownRenderer(highlighter), new InlineTagProcessor(ref -> Optional.empty()), LinkRewriter.identity());
    ApiTypeViewFactory factory = new ApiTypeViewFactory(fragments, highlighter, name -> name.equals("Known") ? Optional.of("../api/Known.html") : Optional.empty());

    private TypeDocs create(SourceElement type) {
        return factory.create(type, "The Title");
    }

    private static MemberView only(TypeDocs docs, String group) {
        return docs.view().groups().stream().filter(g -> g.id().equals(group)).findFirst().orElseThrow().members().getFirst();
    }

    @Test
    void groupsAppearInAFixedOrderAndEmptyGroupsAreOmitted() {
        SourceElement type = type("a.T", "Doc.",
                method("run", "Runs.", "void"),
                member(ElementKind.ENUM_CONSTANT, "ONE", "ONE", "First."),
                member(ElementKind.CONSTRUCTOR, "T", "public T()", "Creates."),
                member(ElementKind.FIELD, "MAX", "int MAX", "Maximum."),
                type("a.T.Inner", "Nested."));

        TypeDocs docs = create(type);

        assertThat(docs.view().groups()).extracting(MemberGroupView::id).containsExactly("constants", "fields", "constructors", "methods", "nested-types");
        assertThat(create(type("a.Empty", "Nothing.")).view().groups()).isEmpty();
    }

    @Test
    void theViewCarriesTheTitleTheNamesAndTheRenderedDescription() {
        TypeDocs docs = create(type("com.acme.Store", "A *store*.\n\n@since 2\n@author Me"));

        assertThat(docs.view().title()).isEqualTo("The Title");
        assertThat(docs.view().name()).isEqualTo("Store");
        assertThat(docs.view().qualifiedName()).isEqualTo("com.acme.Store");
        assertThat(docs.view().packageName()).isEqualTo("com.acme");
        assertThat(docs.view().kind()).isEqualTo("class");
        assertThat(docs.view().descriptionHtml()).contains("<em>store</em>");
        assertThat(docs.view().tags()).extracting(Views.TagView::label).containsExactly("Since", "Author");
    }

    @Test
    void overloadedMethodsShowTheirParameterTypesOthersOnlyEmptyParentheses() {
        TypeDocs docs = create(type("a.T", "",
                method("get", "", "V", param("k", "K")),
                method("get", "", "V", param("k", "K"), param("d", "V")),
                method("size", "", "int")));

        assertThat(docs.view().groups().getFirst().members()).extracting(MemberView::name).containsExactly("get(K)", "get(K, V)", "size()");
        assertThat(docs.toc()).extracting(TocEntry::text).containsExactly("Methods", "get(K)", "get(K, V)", "size()");
        assertThat(docs.toc()).extracting(TocEntry::level).containsExactly(2, 3, 3, 3);
    }

    @Test
    void theParameterTableAppearsOnlyWhenSomethingIsDocumentedAndListsAllParameters() {
        MemberView documented = only(create(type("a.T", "", method("put", "Puts.\n\n@param key the key", "void", param("key", "K"), param("value", "V")))), "methods");
        MemberView undocumented = only(create(type("a.T", "", method("put", "Puts.", "void", param("key", "K")))), "methods");

        assertThat(documented.params()).extracting(Views.ParamView::name).containsExactly("key", "value");
        assertThat(documented.params().getFirst().descriptionHtml()).isEqualTo("the key");
        assertThat(documented.params().get(1).descriptionHtml()).isEmpty();
        assertThat(undocumented.params()).isEmpty();
    }

    @Test
    void returnsAndThrowsOnlyAppearWhenDocumentedAndTypesAreLinkedWhenKnown() {
        MemberView view = only(create(type("a.T", "", method("find", "Finds.\n@return the thing\n@throws IllegalStateException if broken\n@throws java.io.IOException when io fails", "Known"))), "methods");

        assertThat(view.returns().typeHtml()).contains("<a class=\"tok-t\" href=\"../api/Known.html\">Known</a>");
        assertThat(view.returns().descriptionHtml()).isEqualTo("the thing");
        assertThat(view.throwsList()).extracting(Views.ThrowsView::descriptionHtml).containsExactly("if broken", "when io fails");
        assertThat(only(create(type("a.T", "", method("plain", "Plain.", "int"))), "methods").returns()).isNull();
        assertThat(only(create(type("a.T", "", method("plain", "Plain.", "int"))), "methods").throwsList()).isEmpty();
    }

    @Test
    void deprecationComesFromTheTagOrTheAnnotation() {
        MemberView byTag = only(create(type("a.T", "", method("old", "Old.\n@deprecated use new", "void"))), "methods");
        MemberView byAnnotation = only(create(type("a.T", "", member(ElementKind.FIELD, "OLD", "int OLD", "Old.", "@Deprecated"))), "fields");
        MemberView current = only(create(type("a.T", "", method("fresh", "Fresh.", "void"))), "methods");

        assertThat(byTag.deprecated().html()).isEqualTo("use new");
        assertThat(byAnnotation.deprecated().html()).isEmpty();
        assertThat(byAnnotation.deprecated()).isNotNull();
        assertThat(current.deprecated()).isNull();
    }

    @Test
    void tagsAreLabelledAndSeeReferencesBecomeOneList() {
        MemberView view = only(create(type("a.T", "", method("m", "Doc.\n@apiNote careful\n@see Other\n@see \"A Book\"\n@since 1.1", "void"))), "methods");

        assertThat(view.tags()).extracting(Views.TagView::label).containsExactly("API note", "Since", "See also");
        assertThat(view.tags().getLast().html()).contains("<ul>").contains("A Book").contains("<code>Other</code>");
    }

    @Test
    void theSignatureIsHighlightedAndAnnotationsComeFirst() {
        MemberView view = only(create(type("a.T", "", member(ElementKind.FIELD, "X", "public static final int X = 5", "", "@Deprecated(since = \"1\")"))), "fields");

        assertThat(view.signatureHtml()).startsWith("<span class=\"tok-a\">@Deprecated</span>").contains("<span class=\"tok-k\">static</span>").contains("<span class=\"tok-n\">5</span>");
    }

    @Test
    void typeLevelParametersAreListedWithoutTypes() {
        TypeDocs docs = create(type("a.T", "Doc.\n@param <K> the key type\n@param <V> the value type"));

        assertThat(docs.view().params()).extracting(Views.ParamView::name).containsExactly("<K>", "<V>");
        assertThat(docs.view().params().getFirst().typeHtml()).isEmpty();
    }

    @Test
    void searchTextStartsWithTheDescriptionAndEndsWithTheSignatureAndSkipsMarkup() {
        TypeDocs docs = create(type("a.T", "About **things**.", method("run", "Runs `fast`.\n@param n the count", "void", param("n", "int"))));

        assertThat(docs.chunks()).extracting(TextChunk::heading).containsExactly("", "run()");
        assertThat(docs.chunks().get(0).text()).startsWith("About things.").endsWith("public class T");
        TextChunk run = docs.chunks().get(1);
        assertThat(run.headingId()).isEqualTo("method-run-int");
        assertThat(run.text()).startsWith("Runs fast.").contains("the count").endsWith("public void run(int n)").doesNotContain("<");
    }
}
