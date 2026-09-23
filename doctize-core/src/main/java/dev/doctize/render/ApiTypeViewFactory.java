package dev.doctize.render;

import dev.doctize.javadoc.BlockTag;
import dev.doctize.javadoc.JavadocComment;
import dev.doctize.javadoc.JavadocParser;
import dev.doctize.javadoc.Summaries;
import dev.doctize.markdown.CodeHighlighter;
import dev.doctize.markdown.TextChunk;
import dev.doctize.markdown.TocEntry;
import dev.doctize.model.Declaration;
import dev.doctize.model.ElementKind;
import dev.doctize.model.Parameter;
import dev.doctize.model.SourceElement;
import dev.doctize.model.SourceLocation;
import dev.doctize.model.TypeNames;
import dev.doctize.render.Views.DeprecationView;
import dev.doctize.render.Views.MemberGroupView;
import dev.doctize.render.Views.MemberView;
import dev.doctize.render.Views.ParamView;
import dev.doctize.render.Views.ReturnView;
import dev.doctize.render.Views.TagView;
import dev.doctize.render.Views.ThrowsView;
import dev.doctize.render.Views.TypeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Builds the template data of a type page from an extracted {@link SourceElement}. */
final class ApiTypeViewFactory {

    /** Tags that have a dedicated place in the layout; all others are shown as labelled blocks. */
    private static final Set<String> STRUCTURAL_TAGS = Set.of("param", "return", "throws", "exception", "deprecated", "see");

    private record GroupSpec(String id, String title, Predicate<SourceElement> membership) {

        static GroupSpec of(String id, String title, ElementKind kind) {
            return new GroupSpec(id, title, member -> member.declaration().kind() == kind);
        }
    }

    private static final List<GroupSpec> GROUPS = List.of(
            GroupSpec.of("constants", "Enum constants", ElementKind.ENUM_CONSTANT),
            GroupSpec.of("fields", "Fields", ElementKind.FIELD),
            GroupSpec.of("constructors", "Constructors", ElementKind.CONSTRUCTOR),
            GroupSpec.of("methods", "Methods", ElementKind.METHOD),
            new GroupSpec("nested-types", "Nested types", member -> member.declaration().kind().isType()));

    private record MemberDocs(MemberView view, TextChunk chunk) {}

    private final Fragments fragments;
    private final CodeHighlighter highlighter;
    private final Function<String, Optional<String>> typeLinks;
    private final Function<SourceLocation, Optional<String>> sourceLinks;

    ApiTypeViewFactory(
            Fragments fragments,
            CodeHighlighter highlighter,
            Function<String, Optional<String>> typeLinks,
            Function<SourceLocation, Optional<String>> sourceLinks) {
        this.fragments = fragments;
        this.highlighter = highlighter;
        this.typeLinks = typeLinks;
        this.sourceLinks = sourceLinks;
    }

    TypeDocs create(SourceElement type, String title) {
        Declaration declaration = type.declaration();
        JavadocComment doc = JavadocParser.parse(type.comment());
        List<MemberGroupView> groups = new ArrayList<>();
        List<TocEntry> toc = new ArrayList<>();
        List<TextChunk> chunks = new ArrayList<>();
        chunks.add(new TextChunk("", "", introText(declaration, doc), declaration.kind().label()));

        for (GroupSpec spec : GROUPS) {
            List<MemberDocs> members = type.members().stream()
                    .filter(spec.membership())
                    .map(member -> member(member, overloaded(type, member)))
                    .toList();
            if (members.isEmpty()) {
                continue;
            }
            groups.add(new MemberGroupView(spec.id(), spec.title(), members.stream().map(MemberDocs::view).toList()));
            toc.add(new TocEntry(2, spec.title(), spec.id()));
            for (MemberDocs member : members) {
                toc.add(new TocEntry(3, member.view().name(), member.view().id()));
                chunks.add(member.chunk());
            }
        }
        TypeView view = new TypeView(
                title,
                declaration.name(),
                declaration.qualifiedName(),
                declaration.packageName(),
                declaration.kind().label(),
                signature(declaration),
                fragments.html(doc.description(), "type-"),
                deprecation(declaration, doc),
                parameters(doc),
                tags(doc, "type-"),
                groups,
                sourceUrl(type));
        return new TypeDocs(view, toc, chunks);
    }

    private MemberDocs member(SourceElement element, boolean overloaded) {
        Declaration declaration = element.declaration();
        JavadocComment doc = JavadocParser.parse(element.comment());
        String id = Anchors.of(declaration);
        String name = displayName(declaration, overloaded);
        List<ParamView> params = memberParameters(declaration, doc);
        MemberView view = new MemberView(
                id,
                name,
                declaration.kind().label(),
                signature(declaration),
                fragments.inlineHtml(Summaries.firstSentence(doc.description())),
                fragments.html(doc.description(), id + "-"),
                deprecation(declaration, doc),
                params,
                returns(declaration, doc),
                thrown(doc),
                tags(doc, id + "-"),
                sourceUrl(element));
        return new MemberDocs(view, new TextChunk(id, name, memberText(declaration, doc), declaration.kind().label()));
    }

    private String sourceUrl(SourceElement element) {
        return element.location() == null ? "" : sourceLinks.apply(element.location()).orElse("");
    }

    /** Methods and constructors are shown as {@code name()}, or with their parameter types if the name is overloaded. */
    private static String displayName(Declaration declaration, boolean overloaded) {
        boolean callable = declaration.kind() == ElementKind.METHOD || declaration.kind() == ElementKind.CONSTRUCTOR;
        if (!callable) {
            return declaration.name();
        }
        String parameters = overloaded
                ? declaration.parameters().stream().map(p -> TypeNames.erasure(p.type())).collect(Collectors.joining(", "))
                : "";
        return declaration.name() + "(" + parameters + ")";
    }

    private static boolean overloaded(SourceElement type, SourceElement member) {
        Declaration declaration = member.declaration();
        return type.members().stream()
                .map(SourceElement::declaration)
                .filter(other -> other.kind() == declaration.kind() && other.name().equals(declaration.name()))
                .count() > 1;
    }

    private String signature(Declaration declaration) {
        String annotations = declaration.annotations().stream().map(a -> a + "\n").collect(Collectors.joining());
        return highlighter.highlight(annotations + declaration.signature(), "java", typeLinks);
    }

    private String typeHtml(String type) {
        return highlighter.highlight(TypeNames.simplify(type), "java", typeLinks);
    }

    private List<ParamView> parameters(JavadocComment doc) {
        return doc.tags("param").stream()
                .map(tag -> new ParamView(tag.subject(), "", fragments.inlineHtml(tag.text())))
                .toList();
    }

    /** The parameter table is only worth showing if at least one parameter is documented. */
    private List<ParamView> memberParameters(Declaration declaration, JavadocComment doc) {
        boolean anyDocumented = declaration.parameters().stream().anyMatch(p -> doc.paramTag(p.name()).isPresent());
        if (!anyDocumented) {
            return List.of();
        }
        return declaration.parameters().stream().map(parameter -> parameterView(parameter, doc)).toList();
    }

    private ParamView parameterView(Parameter parameter, JavadocComment doc) {
        String description = doc.paramTag(parameter.name()).map(BlockTag::text).orElse("");
        return new ParamView(parameter.name(), typeHtml(parameter.type()), fragments.inlineHtml(description));
    }

    private ReturnView returns(Declaration declaration, JavadocComment doc) {
        return doc.firstTag("return")
                .filter(tag -> declaration.returnType() != null)
                .map(tag -> new ReturnView(typeHtml(declaration.returnType()), fragments.inlineHtml(tag.text())))
                .orElse(null);
    }

    /** Documented exceptions, whether they are declared in the signature or unchecked. */
    private List<ThrowsView> thrown(JavadocComment doc) {
        return doc.tags().stream()
                .filter(tag -> tag.name().equals("throws") || tag.name().equals("exception"))
                .map(tag -> new ThrowsView(typeHtml(tag.subject()), fragments.inlineHtml(tag.text())))
                .toList();
    }

    private DeprecationView deprecation(Declaration declaration, JavadocComment doc) {
        Optional<BlockTag> tag = doc.firstTag("deprecated");
        if (tag.isEmpty() && !declaration.isDeprecated()) {
            return null;
        }
        return new DeprecationView(tag.map(t -> fragments.inlineHtml(t.text())).orElse(""));
    }

    private List<TagView> tags(JavadocComment doc, String idPrefix) {
        List<TagView> views = new ArrayList<>();
        for (BlockTag tag : doc.tagsExcept(STRUCTURAL_TAGS)) {
            views.add(new TagView(TagLabels.labelFor(tag.name()), fragments.html(tag.text(), idPrefix)));
        }
        List<BlockTag> references = doc.tags("see");
        if (!references.isEmpty()) {
            String list = references.stream().map(tag -> "- " + seeReference(tag.text())).collect(Collectors.joining("\n"));
            views.add(new TagView(TagLabels.labelFor("see"), fragments.html(list, idPrefix)));
        }
        return views;
    }

    /** {@code @see} accepts HTML links, quoted titles and references; the latter become {@code {@link}} tags. */
    private static String seeReference(String text) {
        return text.startsWith("<") || text.startsWith("\"") ? text : "{@link " + text + "}";
    }

    private String introText(Declaration declaration, JavadocComment doc) {
        return fragments.plain(doc.description()) + " " + declaration.signature();
    }

    private String memberText(Declaration declaration, JavadocComment doc) {
        String tagText = doc.tags().stream().map(BlockTag::text).collect(Collectors.joining(" "));
        return fragments.plain(doc.description() + "\n\n" + tagText) + " " + declaration.signature();
    }
}
