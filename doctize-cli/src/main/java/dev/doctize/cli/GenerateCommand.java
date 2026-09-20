package dev.doctize.cli;

import com.sun.net.httpserver.SimpleFileServer;
import dev.doctize.Doctize;
import dev.doctize.DoctizeException;
import dev.doctize.GenerationReport;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code doctize generate}: builds the site. */
@Command(name = "generate", description = "Generates the documentation site.", mixinStandardHelpOptions = true)
final class GenerateCommand implements Callable<Integer> {

    private static final Path DEFAULT_CONFIG = Path.of("doctize.yml");

    @Option(names = {"-c", "--config"}, description = "Configuration file. Default: ./doctize.yml if it exists.")
    private Path config;

    @Option(
            names = {"-m", "--model"},
            description = "Compiled classes directory, model file or jar produced by the annotation processor. Repeatable.")
    private List<Path> models = List.of();

    @Option(
            names = "--classes",
            description = "Compiled classes (a directory or jar) for the architecture map to analyze, in addition to the --model locations. Repeatable.")
    private List<Path> classes = List.of();

    @Option(names = "--architecture", description = "Generate the architecture map, a licensed feature. Same as architecture.enabled in the configuration.")
    private boolean architecture;

    @Option(
            names = "--license",
            description = "The license file. Default: $DOCTIZE_LICENSE, ./doctize.license next to the configuration, then ~/.doctize/license.")
    private Path license;

    @Option(names = {"-g", "--guides"}, description = "Directory with Markdown guides. Overrides the configuration.")
    private Path guides;

    @Option(names = {"-t", "--theme"}, description = "Custom theme directory. Overrides the configuration.")
    private Path theme;

    @Option(names = {"-o", "--output"}, description = "Output directory. Default: ${DEFAULT-VALUE}.", defaultValue = "build/docs")
    private Path output;

    @Option(names = "--github-pages", description = "Prepare the output for GitHub Pages (.nojekyll, CNAME).")
    private boolean githubPages;

    @Option(
            names = "--base-url",
            description = "The address the site will be published at, e.g. https://acme.github.io/project/. "
                    + "Enables 404.html, sitemap.xml, robots.txt and canonical links.")
    private String baseUrl;

    @Option(names = "--cname", description = "The custom domain of a GitHub Pages site, e.g. docs.acme.dev.")
    private String customDomain;

    @Option(names = "--fail-on-warnings", description = "Exit with code 1 if generation produced warnings, e.g. unresolved links. For CI.")
    private boolean failOnWarnings;

    @Option(names = "--serve", description = "Serve the generated site on http://localhost:<port> afterwards.")
    private boolean serve;

    @Option(names = "--port", description = "Port for --serve. Default: ${DEFAULT-VALUE}.", defaultValue = "8080")
    private int port;

    @Override
    public Integer call() {
        try {
            GenerationReport report = builder().build().generate();
            System.out.printf(
                    "Generated %d pages in %d sections → %s%n", report.pages(), report.sections(), report.outputDirectory());
            report.license().ifPresent(found -> System.out.println("Licensed to " + found.summary()));
            report.warnings().forEach(warning -> System.err.println("doctize: warning: " + warning));
            if (failOnWarnings && !report.warnings().isEmpty()) {
                System.err.println("doctize: failing because of " + report.warnings().size() + " warning(s), as requested by --fail-on-warnings");
                return 1;
            }
            if (serve) {
                serve(report.outputDirectory());
            }
            return 0;
        } catch (DoctizeException e) {
            System.err.println("doctize: " + e.getMessage());
            return 1;
        }
    }

    private Doctize.Builder builder() {
        Doctize.Builder builder = Doctize.builder().output(output);
        Path configFile = config != null ? config : (Files.isRegularFile(DEFAULT_CONFIG) ? DEFAULT_CONFIG : null);
        if (configFile != null) {
            builder.configFile(configFile);
        }
        models.forEach(builder::model);
        classes.forEach(builder::classes);
        if (architecture) {
            builder.architecture();
        }
        if (license != null) {
            builder.license(license);
        }
        if (guides != null) {
            builder.guides(guides);
        }
        if (theme != null) {
            builder.theme(theme);
        }
        if (githubPages) {
            builder.githubPages();
        }
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        if (customDomain != null) {
            builder.customDomain(customDomain);
        }
        return builder;
    }

    private void serve(Path directory) {
        var address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        var server = SimpleFileServer.createFileServer(address, directory, SimpleFileServer.OutputLevel.NONE);
        server.start();
        System.out.println("Serving at http://localhost:" + port + "/ (Ctrl+C to stop)");
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop(0);
        }
    }
}
