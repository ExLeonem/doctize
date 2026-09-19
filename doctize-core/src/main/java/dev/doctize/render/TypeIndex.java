package dev.doctize.render;

import dev.doctize.model.Declaration;
import dev.doctize.model.SourceElement;
import dev.doctize.model.TypeNames;
import dev.doctize.site.PageContent;
import dev.doctize.site.Site;
import dev.doctize.site.SitePage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Knows every documented type and member, and resolves Javadoc references such as
 * {@code Cache}, {@code com.acme.Cache#get(Object)} or {@code #size} to URLs.
 */
final class TypeIndex {

    private record Entry(SitePage page, SourceElement element, String anchor) {}

    private final Map<String, Entry> types = new HashMap<>();

    TypeIndex(Site site) {
        List<SitePage> typePages = site.pages().stream()
                .filter(page -> page.page().content() instanceof PageContent.Type)
                .toList();
        // Types with a page of their own are registered first, so that a link to a nested type that has
        // its own page leads there rather than to its listing on the owner's page.
        for (SitePage page : typePages) {
            register(new Entry(page, elementOf(page), ""));
        }
        for (SitePage page : typePages) {
            SourceElement owner = elementOf(page);
            owner.members().stream()
                    .filter(member -> member.declaration().kind().isType())
                    .forEach(nested -> registerNested(page, owner, nested));
        }
    }

    private static SourceElement elementOf(SitePage page) {
        return ((PageContent.Type) page.page().content()).element();
    }

    /**
     * @param reference the reference as written in a link tag
     * @param from the page the reference is written on, for references like {@code #member}
     * @return the URL relative to the site root
     */
    Optional<String> resolve(String reference, SitePage from) {
        String trimmed = reference.strip();
        int hash = trimmed.indexOf('#');
        String typeName = hash < 0 ? trimmed : trimmed.substring(0, hash);
        Optional<Entry> type = typeName.isEmpty() ? enclosing(from) : Optional.ofNullable(types.get(typeName));
        if (type.isEmpty()) {
            return Optional.empty();
        }
        if (hash < 0) {
            return Optional.of(url(type.get(), type.get().anchor()));
        }
        return findMember(type.get().element(), trimmed.substring(hash + 1))
                .map(member -> url(type.get(), Anchors.of(member.declaration())));
    }

    /** Links for simple type names found in signatures; only names that identify a documented type. */
    Optional<String> typeUrl(String simpleName) {
        return Optional.ofNullable(types.get(simpleName)).map(entry -> url(entry, entry.anchor()));
    }

    private void register(Entry entry) {
        Declaration declaration = entry.element().declaration();
        types.putIfAbsent(declaration.qualifiedName(), entry);
        types.putIfAbsent(nameWithinPackage(declaration), entry);
        types.putIfAbsent(declaration.name(), entry);
    }

    /** {@code Cache} for a top-level type, {@code Outer.Inner} for a nested one. */
    private static String nameWithinPackage(Declaration declaration) {
        String pkg = declaration.packageName();
        return pkg.isEmpty() ? declaration.qualifiedName() : declaration.qualifiedName().substring(pkg.length() + 1);
    }

    private void registerNested(SitePage page, SourceElement owner, SourceElement nested) {
        Entry entry = new Entry(page, nested, Anchors.of(nested.declaration()));
        types.putIfAbsent(nested.declaration().qualifiedName(), entry);
        types.putIfAbsent(owner.declaration().name() + "." + nested.declaration().name(), entry);
        types.putIfAbsent(nested.declaration().name(), entry);
    }

    private Optional<Entry> enclosing(SitePage page) {
        return page.page().content() instanceof PageContent.Type type
                ? Optional.ofNullable(types.get(type.element().declaration().qualifiedName()))
                : Optional.empty();
    }

    private static Optional<SourceElement> findMember(SourceElement type, String memberReference) {
        int open = memberReference.indexOf('(');
        String name = (open < 0 ? memberReference : memberReference.substring(0, open)).strip();
        List<String> parameterTypes = open < 0 ? null : parameterTypes(memberReference.substring(open));
        return type.members().stream()
                .filter(member -> member.declaration().name().equals(name))
                .filter(member -> parameterTypes == null || sameParameters(member.declaration(), parameterTypes))
                .findFirst();
    }

    private static List<String> parameterTypes(String parenthesized) {
        String inner = parenthesized.substring(1, parenthesized.lastIndexOf(')') < 0
                ? parenthesized.length()
                : parenthesized.lastIndexOf(')'));
        return inner.isBlank()
                ? List.of()
                : Arrays.stream(inner.split(",")).map(TypeNames::erasure).toList();
    }

    /**
     * Compares the parameter types of a reference with a declaration. A type variable such as {@code K}
     * erases to {@code Object}, which the declaration text does not tell us, so {@code Object} in a
     * reference matches any parameter type.
     */
    private static boolean sameParameters(Declaration declaration, List<String> expected) {
        List<String> actual = declaration.parameters().stream()
                .map(parameter -> TypeNames.erasure(parameter.type()))
                .toList();
        return actual.size() == expected.size()
                && IntStream.range(0, actual.size())
                        .allMatch(i -> expected.get(i).equals("Object") || expected.get(i).equals(actual.get(i)));
    }

    private static String url(Entry entry, String anchor) {
        return entry.page().url() + (anchor.isEmpty() ? "" : "#" + anchor);
    }
}
