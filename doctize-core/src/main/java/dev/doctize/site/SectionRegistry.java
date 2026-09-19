package dev.doctize.site;

import dev.doctize.config.SectionSpec;
import dev.doctize.text.Slugs;
import java.util.ArrayList;
import java.util.List;

/** The known sections in navigation order. Sections that pages refer to but nobody declared are added on demand. */
final class SectionRegistry {

    private final List<SectionSpec> sections;

    SectionRegistry(List<SectionSpec> declared) {
        this.sections = new ArrayList<>(declared);
    }

    SectionSpec resolve(String reference) {
        return sections.stream().filter(spec -> spec.matches(reference)).findFirst().orElseGet(() -> declare(reference));
    }

    List<SectionSpec> all() {
        return List.copyOf(sections);
    }

    private SectionSpec declare(String reference) {
        String slug = Slugs.slugify(reference);
        String title = reference.equals(slug) ? Slugs.humanize(reference) : reference;
        SectionSpec created = new SectionSpec(slug, title, "");
        sections.add(created);
        return created;
    }
}
