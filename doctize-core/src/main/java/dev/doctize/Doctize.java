package dev.doctize;

import dev.doctize.annotations.Doc;
import dev.doctize.config.ConfigLoader;
import dev.doctize.config.DoctizeConfig;
import dev.doctize.config.SectionSpec;
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

        List<String> extraCss = publishExtras(output, config.theme().extraCss());
        List<String> extraJs = publishExtras(output, config.theme().extraJs());
        publishThemeAssets(output, theme);
        if (guides != null) {
            publishGuideAssets(output, guides);
        }

        PageRenderer renderer = new PageRenderer(site, new CodeHighlighter(), templates);
        SiteWriter writer = new SiteWriter(
                site,
                renderer,
                new LayoutFactory(site, config.theme(), extraCss, extraJs),
                templates,
                output);
        int pages = writer.write();
        return new GenerationReport(pages, site.sections().size(), output.root(), renderer.warnings());
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
            if (sections.isEmpty()) {
                return config;
            }
            return new DoctizeConfig(
                    config.project(), config.theme(), sections, config.guides(), config.defaultSection(), config.api());
        }
    }
}
