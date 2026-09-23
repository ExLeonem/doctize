package dev.doctize.site;

import dev.doctize.config.ApiSettings;
import dev.doctize.javadoc.JavadocParser;
import dev.doctize.markdown.TitledMarkdown;
import dev.doctize.model.Role;
import dev.doctize.model.SourceElement;
import dev.doctize.model.SourceModel;
import dev.doctize.text.Slugs;
import java.util.List;

/** Turns the elements extracted by the annotation processor into pages. */
public final class ApiPageSource implements PageSource {

    private final List<SourceModel> models;
    private final ApiSettings settings;

    public ApiPageSource(List<SourceModel> models, ApiSettings settings) {
        this.models = List.copyOf(models);
        this.settings = settings;
    }

    @Override
    public List<Page> pages() {
        return models.stream()
                .flatMap(model -> model.elements().stream())
                .map(element -> element.role() == Role.PAGE ? proseOf(element) : referenceOf(element))
                .toList();
    }

    private Page referenceOf(SourceElement element) {
        String name = element.declaration().name();
        String title = element.placement().title().isBlank() ? name : element.placement().title();
        String group = element.placement().group().isBlank()
                ? element.declaration().packageName()
                : element.placement().group();
        String section = element.placement().section().isBlank() ? settings.section() : element.placement().section();
        return new Page(
                element.declaration().qualifiedName(),
                title,
                section,
                group,
                element.placement().order(),
                "",
                element.keywords(),
                new PageContent.Type(withVisibleMembers(element)));
    }

    private SourceElement withVisibleMembers(SourceElement type) {
        List<SourceElement> members = type.members().stream()
                .filter(member -> settings.includeUndocumented() || !member.comment().isBlank())
                .toList();
        return new SourceElement(
                type.role(), type.declaration(), type.comment(), type.placement(), type.keywords(), members, type.snippets(), type.location());
    }

    private Page proseOf(SourceElement element) {
        TitledMarkdown text = TitledMarkdown.split(JavadocParser.parse(element.comment()).description());
        String name = element.declaration().name();
        String title = firstNonBlank(element.placement().title(), text.title().orElse(""), Slugs.humanize(name));
        return new Page(
                Slugs.slugify(title),
                title,
                element.placement().section(),
                element.placement().group(),
                element.placement().order(),
                "",
                element.keywords(),
                new PageContent.Guide(text.body(), "", element.snippets()));
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
