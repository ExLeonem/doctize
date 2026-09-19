package dev.doctize.site;

import dev.doctize.config.DoctizeConfig;
import dev.doctize.text.Slugs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Places pages in sections, orders them and assigns their URLs. This is where the configured
 * structure meets the pages that sources provide.
 */
public final class SiteAssembler {

    private static final Pattern URL_SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final DoctizeConfig config;

    public SiteAssembler(DoctizeConfig config) {
        this.config = config;
    }

    public Site assemble(List<Page> pages, String intro) {
        SectionRegistry registry = new SectionRegistry(config.sections());
        Map<String, List<Page>> pagesBySection = new LinkedHashMap<>();
        for (Page page : pages) {
            String reference = page.section().isBlank() ? config.defaultSection() : page.section();
            pagesBySection
                    .computeIfAbsent(registry.resolve(reference).id(), id -> new ArrayList<>())
                    .add(page);
        }
        List<Section> sections = registry.all().stream()
                .filter(spec -> pagesBySection.containsKey(spec.id()))
                .map(spec -> section(spec.id(), spec.title(), spec.description(), pagesBySection.get(spec.id())))
                .toList();
        return new Site(config.project(), sections, intro);
    }

    /**
     * Pages of a group stay together. Groups are ordered by the lowest {@code order} of their pages
     * (then by name), pages within a group by {@code order} (then by title). Pages without a group
     * form one group of their own.
     */
    private static Comparator<Page> pageOrder(List<Page> pages) {
        Map<String, Integer> groupPosition = pages.stream()
                .collect(Collectors.toMap(Page::group, Page::order, Math::min));
        return Comparator.<Page>comparingInt(page -> groupPosition.get(page.group()))
                .thenComparing(Page::group, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(Page::order)
                .thenComparing(Page::title, String.CASE_INSENSITIVE_ORDER);
    }

    private static Section section(String id, String title, String description, List<Page> pages) {
        Set<String> usedSlugs = new HashSet<>();
        List<SitePage> placed = pages.stream()
                .sorted(pageOrder(pages))
                .map(page -> place(page, id, uniqueSlug(page, usedSlugs)))
                .toList();
        return new Section(id, title, description, placed);
    }

    private static SitePage place(Page page, String sectionId, String slug) {
        return new SitePage(page, sectionId, sectionId + "/" + slug + ".html");
    }

    /** Keeps slugs that are already safe in a URL (e.g. {@code dev.acme.Cache}), slugifies all others. */
    private static String uniqueSlug(Page page, Set<String> used) {
        String base = URL_SAFE.matcher(page.slug()).matches() ? page.slug() : Slugs.slugify(page.slug());
        String slug = base;
        // Compared case-insensitively: the site may be built or served on a case-insensitive file system.
        for (int suffix = 2; !used.add(slug.toLowerCase(Locale.ROOT)); suffix++) {
            slug = base + "-" + suffix;
        }
        return slug;
    }
}
