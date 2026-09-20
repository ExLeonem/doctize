package dev.doctize;

import dev.doctize.annotations.Doc;
import dev.doctize.architecture.ArchitecturePageSource;
import dev.doctize.config.ConfigLoader;
import dev.doctize.config.DoctizeConfig;
import dev.doctize.license.Feature;
import dev.doctize.license.LicenseLocator;
import dev.doctize.license.LicenseStatus;
import dev.doctize.license.LicenseVerifier;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final List<Path> classLocations;
    private final Path licenseFile;
    private final String licenseKey;
    private final LicenseVerifier licenseVerifier;
    private final Map<String, String> licenseEnvironment;
    private final Path licenseHome;

    private Doctize(Builder builder) {
        this.baseDirectory = builder.baseDirectory;
        this.config = builder.effectiveConfig();
        this.modelLocations = List.copyOf(builder.modelLocations);
        this.guidesDirectory = builder.guidesDirectory != null ? builder.guidesDirectory : resolveConfigured(config.guides());
        this.themeDirectory = builder.themeDirectory != null ? builder.themeDirectory : resolveConfigured(config.theme().path());
        this.outputDirectory = builder.outputDirectory;
        this.extraSources = List.copyOf(builder.extraSources);
        this.classLocations = List.copyOf(builder.classLocations);
        this.licenseFile = builder.licenseFile;
        this.licenseKey = builder.licenseKey;
        this.licenseVerifier = builder.licenseVerifier != null ? builder.licenseVerifier : LicenseVerifier.vendor();
        this.licenseEnvironment = builder.licenseEnvironment != null ? builder.licenseEnvironment : System.getenv();
        this.licenseHome = builder.licenseEnvironment != null ? builder.licenseHome : Path.of(System.getProperty("user.home"));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Generates the site into the output directory. */
    public GenerationReport generate() {
        GuideDirectory guides = guidesDirectory == null ? null : new GuideDirectory(guidesDirectory);
        LicenseStatus license = LicenseStatus.of(
                LicenseLocator.locate(licenseFile, licenseKey, baseDirectory, licenseEnvironment, licenseHome), licenseVerifier);
        List<String> licenseWarnings = new ArrayList<>();
        ArchitecturePageSource architecture = architectureSource(license, licenseWarnings);
        Site site = new SiteAssembler(config).assemble(collectPages(guides, architecture), guides == null ? "" : guides.intro());
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
        warnings.addAll(licenseWarnings);
        if (architecture != null) {
            warnings.addAll(architecture.warnings());
        }
        return new GenerationReport(pages, site.sections().size(), output.root(), warnings, license.license());
    }

    /** The architecture map is only generated if it is switched on and the license allows it; otherwise the reason is a warning. */
    private ArchitecturePageSource architectureSource(LicenseStatus license, List<String> warnings) {
        if (!config.architecture().enabled()) {
            return null;
        }
        Optional<String> denial = license.denialFor(Feature.ARCHITECTURE);
        if (denial.isPresent()) {
            warnings.add("The architecture map was skipped. " + denial.get());
            return null;
        }
        Set<Path> roots = new LinkedHashSet<>(classLocations);
        modelLocations.stream().filter(location -> !location.getFileName().toString().endsWith(".json")).forEach(roots::add);
        return new ArchitecturePageSource(config.architecture(), List.copyOf(roots));
    }

    private static List<String> warnings(PageRenderer renderer, HostingSettings hosting, Optional<BaseUrl> baseUrl) {
        List<String> warnings = new ArrayList<>(renderer.warnings());
        if (hosting.githubPages() && baseUrl.isEmpty()) {
            warnings.add("The address of the site is unknown, so 404.html, sitemap.xml, robots.txt and canonical links were skipped."
                    + " Set hosting.baseUrl, or project.repository to a github.com URL.");
        }
        return warnings;
    }

    private List<Page> collectPages(GuideDirectory guides, ArchitecturePageSource architecture) {
        List<SourceModel> models = modelLocations.stream().map(ModelLocator::load).toList();
        List<PageSource> sources = new ArrayList<>();
        sources.add(new ApiPageSource(models, config.api()));
        if (guides != null) {
            sources.add(guides);
        }
        if (architecture != null) {
            sources.add(architecture);
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
        private final List<Path> classLocations = new ArrayList<>();
        private Path licenseFile;
        private String licenseKey;
        private LicenseVerifier licenseVerifier;
        private Map<String, String> licenseEnvironment;
        private Path licenseHome;
        private boolean architecture;
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

        /**
         * Adds compiled classes (a directory or a jar) for the architecture map to analyze. The locations given to
         * {@link #model(Path)} are analyzed as well, so this is for classes that have nothing to document, such as a
         * module without annotated types. May be called repeatedly.
         */
        public Builder classes(Path location) {
            this.classLocations.add(location);
            return this;
        }

        /** Switches the architecture map on, as {@code architecture.enabled: true} does. It needs a license. */
        public Builder architecture() {
            this.architecture = true;
            return this;
        }

        /** Uses the license in this file, instead of searching for one. */
        public Builder license(Path file) {
            this.licenseFile = file;
            return this;
        }

        /** Uses this license, given as text, instead of searching for one. */
        public Builder licenseKey(String license) {
            this.licenseKey = license;
            return this;
        }

        /**
         * Checks licenses with another verifier than the one of the vendor of Doctize, for example one for the licenses
         * of a product you build on Doctize.
         */
        public Builder licenseVerifier(LicenseVerifier verifier) {
            this.licenseVerifier = verifier;
            return this;
        }

        /**
         * Where to search for a license, if none is given: these environment variables and this home directory instead of the
         * real ones. Mostly for tests, which must not depend on the license of whoever runs them.
         */
        public Builder licenseSearch(Map<String, String> environment, Path home) {
            this.licenseEnvironment = Map.copyOf(environment);
            this.licenseHome = home;
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
                    config.hosting().overriddenBy(baseUrl, githubPages, customDomain),
                    architecture ? config.architecture().turnedOn() : config.architecture());
        }
    }
}
