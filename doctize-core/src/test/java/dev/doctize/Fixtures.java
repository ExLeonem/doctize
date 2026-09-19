package dev.doctize;

import dev.doctize.config.DoctizeConfig;
import dev.doctize.config.SectionSpec;
import dev.doctize.model.Declaration;
import dev.doctize.model.ElementKind;
import dev.doctize.model.Parameter;
import dev.doctize.model.Placement;
import dev.doctize.model.Role;
import dev.doctize.model.SourceElement;
import dev.doctize.site.Page;
import dev.doctize.site.PageContent;
import dev.doctize.site.Site;
import dev.doctize.site.SiteAssembler;
import java.util.Arrays;
import java.util.List;

/** Small builders for the model objects that many tests need. */
public final class Fixtures {

    private Fixtures() {}

    public static Parameter param(String name, String type) {
        return new Parameter(name, type);
    }

    public static SourceElement method(String name, String comment, String returnType, Parameter... parameters) {
        String signature = "public " + returnType + " " + name + "("
                + String.join(", ", Arrays.stream(parameters).map(p -> p.type() + " " + p.name()).toList()) + ")";
        return new SourceElement(
                Role.API,
                new Declaration(ElementKind.METHOD, name, "a.T." + name, "a", signature, List.of(), List.of(parameters), returnType, List.of()),
                comment, Placement.none(), List.of(), List.of());
    }

    public static SourceElement member(ElementKind kind, String name, String signature, String comment, String... annotations) {
        return new SourceElement(
                Role.API,
                new Declaration(kind, name, "a.T." + name, "a", signature, List.of(annotations), List.of(), null, List.of()),
                comment, Placement.none(), List.of(), List.of());
    }

    public static SourceElement type(String qualifiedName, String comment, SourceElement... members) {
        return type(ElementKind.CLASS, qualifiedName, comment, Placement.none(), members);
    }

    public static SourceElement type(ElementKind kind, String qualifiedName, String comment, Placement placement, SourceElement... members) {
        String name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        String pkg = packageOf(qualifiedName);
        return new SourceElement(
                Role.API,
                new Declaration(kind, name, qualifiedName, pkg, "public " + kind.label() + " " + name, List.of(), List.of(), null, List.of()),
                comment, placement, List.of(), List.of(members));
    }

    /** The leading lower-case segments: {@code com.acme} for {@code com.acme.Cache.Entry}. */
    private static String packageOf(String qualifiedName) {
        List<String> segments = new java.util.ArrayList<>();
        for (String segment : qualifiedName.split("\\.")) {
            if (Character.isLowerCase(segment.charAt(0))) {
                segments.add(segment);
            } else {
                break;
            }
        }
        return String.join(".", segments);
    }

    public static Page apiPage(SourceElement type) {
        return new Page(type.declaration().qualifiedName(), type.declaration().name(), "api", type.declaration().packageName(), 1, "", List.of(), new PageContent.Type(type));
    }

    public static Page guide(String slug, String title, String section, String markdown, String sourcePath) {
        return new Page(slug, title, section, "", 1, "", List.of(), new PageContent.Guide(markdown, sourcePath));
    }

    /** A site with the given pages, in sections created on demand. */
    public static Site site(Page... pages) {
        return new SiteAssembler(DoctizeConfig.defaults()).assemble(List.of(pages), "");
    }

    public static Site siteWithSections(List<SectionSpec> sections, Page... pages) {
        return new SiteAssembler(new DoctizeConfig(null, null, sections, null, null, null, null)).assemble(List.of(pages), "");
    }
}
