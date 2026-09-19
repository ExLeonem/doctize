package dev.doctize.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.doctize.testing.Javac;
import dev.doctize.testing.Processes;
import dev.doctize.testing.SiteCrawler;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The installed {@code doctize} command, run as a real process the way a user runs it: through its start script,
 * with nothing but arguments, files and exit codes to look at.
 */
class CliDistributionSystemTest {

    static final Duration TIMEOUT = Duration.ofMinutes(2);
    static Path script;
    static Map<String, String> environment;

    @TempDir
    Path work;

    @BeforeAll
    static void locateTheInstalledCli() {
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"), "the start script is a POSIX shell script");
        script = Path.of(System.getProperty("doctize.cli.home"), "bin", "doctize");
        assertThat(script).as("run `./gradlew :doctize-cli:installDist` first").exists();
        environment = Map.of("JAVA_HOME", System.getProperty("java.home"));
    }

    private Processes.Result doctize(Path directory, String... arguments) {
        List<String> command = new ArrayList<>(List.of(script.toString()));
        command.addAll(List.of(arguments));
        return Processes.run(command, environment, directory, TIMEOUT);
    }

    @Test
    void reportsTheVersionOfTheBuild() {
        Processes.Result result = doctize(work, "--version");

        assertThat(result.exitCode()).as(result.toString()).isZero();
        assertThat(result.out().trim()).isEqualTo("doctize " + System.getProperty("doctize.version"));
    }

    @Test
    void helpListsTheCommandsAndTheirOptions() {
        Processes.Result help = doctize(work, "--help");
        Processes.Result generate = doctize(work, "generate", "--help");

        assertThat(help.exitCode()).isZero();
        assertThat(help.out()).contains("generate", "init");
        assertThat(generate.out()).contains("--model", "--output", "--github-pages", "--base-url", "--cname", "--serve");
    }

    @Test
    void unknownCommandsAndOptionsAreUsageErrors() {
        Processes.Result result = doctize(work, "frobnicate");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.err()).contains("frobnicate");
        assertThat(doctize(work, "generate", "--no-such-option").exitCode()).isEqualTo(2);
    }

    @Test
    void initThenGenerateProducesAWorkingSiteFromNothing() throws IOException {
        Path project = work.resolve("my-lib");
        Files.createDirectories(project);

        Processes.Result init = doctize(project, "init", "--name", "My Lib");
        Processes.Result generate = doctize(project, "generate", "--output", "site");

        assertThat(init.exitCode()).as(init.toString()).isZero();
        assertThat(project.resolve("doctize.yml")).exists();
        assertThat(generate.exitCode()).as(generate.toString()).isZero();
        assertThat(generate.out()).contains("Generated 2 pages in 2 sections");
        assertThat(Files.readString(project.resolve("site/index.html"))).contains("My Lib");
        assertThat(SiteCrawler.check(project.resolve("site"), "")).isEmpty();
    }

    @Test
    void userErrorsAreOneLineOnStderrWithExitCodeOneAndNoStackTrace() {
        Processes.Result result = doctize(work, "generate", "--model", work.resolve("missing").toString(), "--output", "site");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.err()).startsWith("doctize: ").doesNotContain("Exception").doesNotContain("\tat ");
        assertThat(result.err().lines().count()).isEqualTo(1);
    }

    @Test
    void documentsARealCompiledLibraryForGithubPages() throws IOException {
        Path project = SamplesForSystemTests.copy("sample-shop", work);
        Path classes = project.resolve("build/classes");
        Javac.compile(project.resolve("java"), classes, System.getProperty("doctize.consumer.classpath"), System.getProperty("doctize.consumer.processorpath"));

        Processes.Result result = doctize(project, "generate", "--config", "doctize.yml", "--model", classes.toString(),
                "--output", "site", "--github-pages", "--base-url", "https://example.github.io/sample-shop", "--cname", "shop.example.dev");

        assertThat(result.exitCode()).as(result.toString()).isZero();
        assertThat(result.err()).isEmpty();
        Path site = project.resolve("site");
        assertThat(Files.readString(site.resolve("CNAME"))).isEqualTo("shop.example.dev\n");
        assertThat(site.resolve(".nojekyll")).exists();
        assertThat(Files.readString(site.resolve("sitemap.xml"))).contains("https://example.github.io/sample-shop/reference/shop.Cart.html");
        assertThat(SiteCrawler.check(site, "/sample-shop/")).isEmpty();
    }

    @Test
    void serveAnswersOverHttpUntilItIsStopped() throws Exception {
        Path project = work.resolve("served");
        Files.createDirectories(project);
        assertThat(doctize(project, "init").exitCode()).isZero();
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Process server = Processes.start(List.of(script.toString(), "generate", "--output", "site", "--serve", "--port", String.valueOf(port)), environment, project);
        try {
            HttpResponse<String> home = get(port, "/index.html", server, project);

            assertThat(home.statusCode()).isEqualTo(200);
            assertThat(home.body()).contains("<title>");
            assertThat(get(port, "/search-index.js", server, project).body()).startsWith("window.DOCTIZE_SEARCH_INDEX");
            assertThat(get(port, "/assets/css/theme.css", server, project).statusCode()).isEqualTo(200);
            assertThat(get(port, "/does/not/exist.html", server, project).statusCode()).isEqualTo(404);
            assertThat(server.isAlive()).isTrue();
        } finally {
            server.destroy();
            assertThat(server.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private static HttpResponse<String> get(int port, String path, Process server, Path project) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            try {
                return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException notYetListening) {
                if (System.nanoTime() > deadline || !server.isAlive()) {
                    throw new AssertionError("the server did not come up:\n" + Processes.read(project, "stderr"), notYetListening);
                }
                Thread.sleep(200);
            }
        }
    }
}
