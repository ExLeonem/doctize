package dev.doctize.system;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.testing.Javac;
import dev.doctize.testing.Processes;
import dev.doctize.testing.SiteCrawler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A downstream project, simulated: its build resolves the published artifacts (from the staging repository, with
 * their transitive dependencies from Maven Central, by Gradle's resolver reading the published metadata), compiles
 * a library with the published annotation processor, and generates its documentation with the published CLI.
 * No class of this repository's own build output is on any of these class paths.
 */
class ConsumerSystemTest {

    static final String COMPILE_CLASSPATH = System.getProperty("doctize.consumer.classpath");
    static final String PROCESSOR_PATH = System.getProperty("doctize.consumer.processorpath");

    @TempDir
    Path work;

    @Test
    void theClassPathsConsistOfPublishedArtifactsAndTheirDependencies() {
        List<String> processorPath = List.of(PROCESSOR_PATH.split(java.io.File.pathSeparator));
        String version = System.getProperty("doctize.version");

        assertThat(processorPath).anyMatch(entry -> entry.endsWith("/staging-deploy/dev/doctize/doctize-processor/" + version + "/doctize-processor-" + version + ".jar"))
                .anyMatch(entry -> entry.endsWith("/staging-deploy/dev/doctize/doctize-core/" + version + "/doctize-core-" + version + ".jar"))
                .anyMatch(entry -> entry.contains("jackson-databind"))
                .anyMatch(entry -> entry.contains("commonmark"))
                .anyMatch(entry -> entry.contains("jmustache"))
                .anyMatch(entry -> entry.contains("picocli"))
                .noneMatch(entry -> entry.contains("/build/classes/") || entry.contains("/build/libs/"));
        assertThat(COMPILE_CLASSPATH).contains("doctize-annotations-" + version + ".jar");
    }

    @Test
    void thePublishedProcessorExtractsTheModelOfALibraryCompiledAgainstThePublishedAnnotations() throws IOException {
        Path project = SamplesForSystemTests.copy("sample-shop", work);
        Path classes = project.resolve("build/classes");

        Javac.compile(project.resolve("java"), classes, COMPILE_CLASSPATH, PROCESSOR_PATH);

        String model = Files.readString(classes.resolve("META-INF/doctize/model.json"));
        assertThat(model).contains("\"qualifiedName\" : \"shop.Cart\"", "\"qualifiedName\" : \"shop.Cart.Line\"", "\"role\" : \"PAGE\"");
    }

    @Test
    void annotatedLibrariesNeedNoDoctizeDependencyAtRuntime() throws IOException {
        Path project = SamplesForSystemTests.copy("sample-shop", work);
        Path classes = project.resolve("build/classes");
        Javac.compile(project.resolve("java"), classes, COMPILE_CLASSPATH, PROCESSOR_PATH);

        for (String type : List.of("shop/Cart.class", "shop/Item.class", "shop/Status.class", "shop/Pricing.class", "shop/package-info.class")) {
            Path classFile = classes.resolve(type);
            if (Files.exists(classFile)) {
                assertThat(new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1))
                        .as(type + " must not mention the Doctize annotations, which have source retention")
                        .doesNotContain("dev/doctize");
            }
        }
        assertThat(classes.resolve("shop/Cart.class")).exists();
    }

    @Test
    void thePublishedCliDocumentsTheLibraryEndToEnd() throws IOException {
        Path project = SamplesForSystemTests.copy("sample-shop", work);
        Path classes = project.resolve("build/classes");
        Javac.compile(project.resolve("java"), classes, COMPILE_CLASSPATH, PROCESSOR_PATH);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();

        List<String> command = new ArrayList<>(List.of(java, "-cp", PROCESSOR_PATH, "dev.doctize.cli.DoctizeCli", "generate",
                "--config", "doctize.yml", "--model", classes.toString(), "--output", "site"));
        Processes.Result result = Processes.run(command, Map.of(), project, Duration.ofMinutes(2));

        assertThat(result.exitCode()).as(result.toString()).isZero();
        assertThat(result.err()).isEmpty();
        assertThat(result.out()).contains("Generated 10 pages in 5 sections");
        Path site = project.resolve("site");
        assertThat(Files.readString(site.resolve("reference/shop.Cart.html"))).contains("add(Item, int)", "the item to add");
        assertThat(Files.readString(site.resolve("assets/css/theme.css"))).contains("--accent");
        assertThat(SiteCrawler.check(site, "")).isEmpty();
    }
}
