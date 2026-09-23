package dev.doctize;

import dev.doctize.annotations.Doc;
import dev.doctize.config.ConfigLoader;
import dev.doctize.config.DoctizeConfig;
import dev.doctize.diagram.MermaidAssets;
import dev.doctize.config.SectionSpec;
import dev.doctize.hosting.BaseUrl;
import dev.doctize.hosting.HostingFiles;
import dev.doctize.hosting.HostingSettings;
import dev.doctize.markdown.CodeHighlighter;
import dev.doctize.model.ModelLocator;
import dev.doctize.model.SourceModel;
import dev.doctize.output.OutputDirectory;
import dev.doctize.output.SiteWriter;
import dev.doctize.render.LayoutFactory;
import dev.doctize.render.PageRenderer;
import dev.doctize.site.ApiPageSource;
import dev.doctize.site.GuideDirectory;
import dev.doctize.site.Page;
import dev.doctize.site.PageSource;
import dev.doctize.site.Site;
import dev.doctize.site.SiteAssembler;
import dev.doctize.theme.TemplateEngine;
import dev.doctize.theme.Theme;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generates a documentation site from the Javadoc of annotated types and from Markdown guides.
 *
 * <pre>{@code
 * Doctize.builder()
 *         .configFile(Path.of("doctize.yml"))
 *         .model(Path.of("build/classes/java/main"))
 *         .output(Path.of("build/docs"))
 *         .build()
 *         .generate();
 * }</pre>
 *
 * <p>The structure of the site can be declared in the configuration file, or in code with
 * {@link Builder#section(String)}. Additional pages can be contributed with
 * {@link Builder#pageSource(PageSource)}.
 */
@Doc(group = "Generator", order = 10)
public final class Doctize {

    private static final String CUSTOM_ASSETS = "assets/custom/";

    private final DoctizeConfig config;
    private final Path baseDirectory;
    private final List<Path> modelLocations;
    private final Path guidesDirectory;
    private final Path themeDirectory;
    private final Path outputDirectory;
    private final List<PageSource> extraSources;

    private Doctize(Builder builder) {
        this.baseDirectory = builder.baseDirectory;
        this.config = builder.effectiveConfig();
        this.modelLocations = List.copyOf(builder.modelLocations);
        this.guidesDirectory = builder.guidesDirectory != null ? builder.guidesDirectory : resolveConfigured(config.guides());
        this.themeDirectory = builder.themeDirectory != null ? builder.themeDirectory : resolveConfigured(config.theme().path());
        this.outputDirectory = builder.outputDirectory;
        this.extraSources = List.copyOf(builder.extraSources);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Generates the site into the output directory. */
    public GenerationReport generate() {
        GuideDirectory guides = guidesDirectory == null ? null : new GuideDirectory(guidesDirectory);
        Site site = new SiteAssembler(config).assemble(collectPages(guides), guides == null ? "" : guides.intro());
        if (site.pages().isEmpty()) {
            throw new DoctizeException("Nothing to document: no annotated types were found in the model"
                    + " and no guides were provided.");
        }
        Theme theme = themeDirectory == null ? Theme.builtIn() : Theme.customized(themeDirectory);
        TemplateEngine templates = new TemplateEngine(theme);
        OutputDirectory output = new OutputDirectory(outputDirectory);
        output.prepare();

        HostingSettings hosting = config.hosting();
        Optional<BaseUrl> baseUrl = hosting.resolveBaseUrl(config.project().repository());

        List<String> extraCss = publishExtras(output, config.theme().extraCss());
        List<String> extraJs = publishExtras(output, config.theme().extraJs());
        publishThemeAssets(output, theme);
        if (MermaidAssets.usedBy(site)) {
            output.write(MermaidAssets.SITE_PATH, MermaidAssets.script());
        }
        if (guides != null) {
            publishGuideAssets(output, guides);
        }

        PageRenderer renderer = new PageRenderer(site, new CodeHighlighter(), templates);
        SiteWriter writer = new SiteWriter(
                site,
                renderer,
                new LayoutFactory(site, config.theme(), extraCss, extraJs, baseUrl),
                templates,
                output,
                baseUrl);
        int pages = writer.write();
        new HostingFiles(output, hosting, baseUrl).write(site);
        List<String> warnings = warnings(renderer, hosting, baseUrl);
        return new GenerationReport(pages, site.sections().size(), output.root(), warnings);
    }

    private static List<String> warnings(PageRenderer renderer, HostingSettings hosting, Optional<BaseUrl> baseUrl) {
        List<String> warnings = new ArrayList<>(renderer.warnings());
        if (hosting.githubPages() && baseUrl.isEmpty()) {
            warnings.add("The address of the site is unknown, so 404.html, sitemap.xml, robots.txt and canonical links were skipped."
                    + " Set hosting.baseUrl, or project.repository to a github.com URL.");
        }
        return warnings;
    }

    private List<Page> collectPages(GuideDirectory guides) {
        List<SourceModel> models = modelLocations.stream().map(ModelLocator::load).toList();
        List<PageSource> sources = new ArrayList<>();
        sources.add(new ApiPageSource(models, config.api()));
        if (guides != null) {
            sources.add(guides);
        }
        sources.addAll(extraSources);
        return sources.stream().flatMap(source -> source.pages().stream()).toList();
    }

    private static void publishThemeAssets(OutputDirectory output, Theme theme) {
        for (Map.Entry<String, byte[]> asset : theme.assets().entrySet()) {
            output.write("assets/" + asset.getKey(), asset.getValue());
        }
    }

    private static void publishGuideAssets(OutputDirectory output, GuideDirectory guides) {
        for (Path asset : guides.assets()) {
            String relative = guides.root().relativize(asset).toString().replace('\\', '/');
            output.copy("assets/content/" + relative, asset);
        }
    }

    /** Copies user supplied stylesheets or scripts next to the theme's assets; returns their URLs. */
    private List<String> publishExtras(OutputDirectory output, List<String> files) {
        List<String> urls = new ArrayList<>();
        for (String file : files) {
            Path source = baseDirectory.resolve(file);
            String url = CUSTOM_ASSETS + source.getFileName();
            output.copy(url, source);
            urls.add(url);
        }
        return urls;
    }

    private Path resolveConfigured(String path) {
        return path.isBlank() ? null : baseDirectory.resolve(path);
    }

    /** Collects settings; every setting given here wins over the configuration file. */
    @Doc(title = "Doctize.Builder", group = "Generator", order = 11)
    public static final class Builder {
        private DoctizeConfig config = DoctizeConfig.defaults();
        private Path baseDirectory = Path.of("").toAbsolutePath();
        private final List<Path> modelLocations = new ArrayList<>();
        private final List<SectionSpec> sections = new ArrayList<>();
        private final List<PageSource> extraSources = new ArrayList<>();
        private Path guidesDirectory;
        private Path themeDirectory;
        private Path outputDirectory = Path.of("build", "docs");
        private String baseUrl;
        private Boolean githubPages;
        private String customDomain;

        private Builder() {}

        /** Uses a {@code doctize.yml}; relative paths inside it are resolved against its directory. */
        public Builder configFile(Path file) {
            this.config = ConfigLoader.load(file);
            this.baseDirectory = file.toAbsolutePath().getParent();
            return this;
        }

        public Builder config(DoctizeConfig config) {
            this.config = config;
            return this;
        }

        /** Where relative paths from the configuration are resolved. Defaults to the working directory. */
        public Builder baseDirectory(Path directory) {
            this.baseDirectory = directory.toAbsolutePath();
            return this;
        }

        /** Adds the compiled output of a module (or a model file or jar) to document. May be called repeatedly. */
        public Builder model(Path location) {
            this.modelLocations.add(location);
            return this;
        }

        public Builder guides(Path directory) {
            this.guidesDirectory = directory;
            return this;
        }

        /** A custom theme directory that is layered over the built-in theme. */
        public Builder theme(Path directory) {
            this.themeDirectory = directory;
            return this;
        }

        public Builder output(Path directory) {
            this.outputDirectory = directory;
            return this;
        }

        /**
         * Tells Doctize where the site will be published, e.g. {@code https://acme.github.io/cache/}. With a known
         * address the output also contains a {@code 404.html} that works at any depth, {@code sitemap.xml},
         * {@code robots.txt} and canonical links. Replaces {@code hosting.baseUrl} of the configuration file.
         *
         * @param baseUrl an absolute {@code http(s)} URL
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Prepares the output for GitHub Pages: adds {@code .nojekyll}, so that GitHub publishes the files as they are.
         * Unless {@link #baseUrl(String)} says otherwise, the address is derived from {@code project.repository}.
         */
        public Builder githubPages() {
            this.githubPages = true;
            return this;
        }

        /** The custom domain of a GitHub Pages site, e.g. {@code docs.acme.dev}; written to the {@code CNAME} file. */
        public Builder customDomain(String domain) {
            this.customDomain = domain;
            return this;
        }

        /**
         * Declares a section in code. The declaration order is the navigation order; once any
         * section is declared here, the sections of the configuration file are ignored.
         */
        public Builder section(String title) {
            return section(new SectionSpec("", title, ""));
        }

        public Builder section(SectionSpec section) {
            this.sections.add(section);
            return this;
        }

        /** Contributes additional pages, e.g. a changelog generated from a file. */
        public Builder pageSource(PageSource source) {
            this.extraSources.add(source);
            return this;
        }

        public Doctize build() {
            return new Doctize(this);
        }

        private DoctizeConfig effectiveConfig() {
            return new DoctizeConfig(
                    config.project(),
                    config.theme(),
                    sections.isEmpty() ? config.sections() : sections,
                    config.guides(),
                    config.defaultSection(),
                    config.api(),
                    config.hosting().overriddenBy(baseUrl, githubPages, customDomain));
        }
    }
}
