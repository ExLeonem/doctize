package dev.doctize.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.model.Declaration;
import dev.doctize.model.ElementKind;
import dev.doctize.model.Placement;
import dev.doctize.model.Role;
import dev.doctize.model.SourceElement;
import dev.doctize.model.SourceModel;
import dev.doctize.model.SourceModelJson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class DoctizeCliTest {

    @TempDir
    Path dir;

    private record Result(int exitCode, String out, String err) {}

    private static Result run(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true));
            System.setErr(new PrintStream(err, true));
            int code = new CommandLine(new DoctizeCli()).execute(args);
            return new Result(code, out.toString(), err.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private Path writeModel() throws IOException {
        Declaration type = new Declaration(ElementKind.CLASS, "Store", "a.Store", "a", "public class Store", List.of(), List.of(), null, List.of());
        SourceModel model = SourceModel.of(List.of(new SourceElement(Role.API, type, "A store.", Placement.none(), List.of(), List.of())));
        Path file = dir.resolve("model.json");
        try (var out = Files.newOutputStream(file)) {
            SourceModelJson.write(model, out);
        }
        return file;
    }

    @Test
    void versionComesFromTheBuildNotFromASourceLiteral() {
        Result result = run("--version");

        assertThat(result.exitCode()).isZero();
        assertThat(result.out().trim()).matches("doctize \\d+\\.\\d+\\.\\d+.*").doesNotContain("${version}").doesNotContain("unknown");
    }

    @Test
    void generateWritesTheSite() throws IOException {
        Path output = dir.resolve("site");

        Result result = run("generate", "--model", writeModel().toString(), "--output", output.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.out()).contains("Generated 1 pages in 1 sections");
        assertThat(output.resolve("index.html")).exists();
        assertThat(output.resolve("api-reference/a.Store.html")).exists();
    }

    @Test
    void generateReportsUserErrorsWithoutAStackTrace() {
        Result result = run("generate", "--model", dir.resolve("missing.json").toString(), "--output", dir.resolve("o").toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.err()).startsWith("doctize: ").doesNotContain("Exception");
    }

    @Test
    void initCreatesStarterFilesThatGenerateASite() throws IOException {
        Path project = dir.resolve("my-lib");
        Files.createDirectories(project);

        Result init = run("init", project.toString(), "--name", "My Lib");
        Result generate = run("generate", "--config", project.resolve("doctize.yml").toString(), "--output", project.resolve("out").toString());

        assertThat(init.exitCode()).isZero();
        assertThat(project.resolve("docs/setup/installation.md")).exists();
        assertThat(generate.exitCode()).as(generate.err()).isZero();
        assertThat(Files.readString(project.resolve("out/setup/installation.html"))).contains("data-tab=\"Gradle\"");
        assertThat(Files.readString(project.resolve("out/index.html"))).contains("My Lib");
    }

    @Test
    void generateCanPrepareTheOutputForGithubPages() throws IOException {
        Path output = dir.resolve("site");

        Result result = run("generate", "--model", writeModel().toString(), "--output", output.toString(),
                "--github-pages", "--base-url", "https://acme.github.io/store", "--cname", "docs.acme.dev");

        assertThat(result.exitCode()).isZero();
        assertThat(result.err()).isEmpty();
        assertThat(output.resolve(".nojekyll")).exists();
        assertThat(Files.readString(output.resolve("CNAME"))).isEqualTo("docs.acme.dev\n");
        assertThat(Files.readString(output.resolve("404.html"))).contains("href=\"/store/index.html\"");
    }

    @Test
    void generateWarnsWhenGithubPagesIsRequestedButTheAddressIsUnknown() throws IOException {
        Result result = run("generate", "--model", writeModel().toString(), "--output", dir.resolve("site").toString(), "--github-pages");

        assertThat(result.exitCode()).isZero();
        assertThat(result.err()).contains("doctize: warning:").contains("address of the site is unknown");
    }

    @Test
    void failOnWarningsTurnsWarningsIntoAFailureButOnlyWhenAsked() throws IOException {
        Path guides = dir.resolve("docs");
        Files.createDirectories(guides);
        Files.writeString(guides.resolve("page.md"), "# Page\n\nSee {@link Nowhere}.\n");

        Result lenient = run("generate", "--guides", guides.toString(), "--output", dir.resolve("a").toString());
        Result strict = run("generate", "--guides", guides.toString(), "--output", dir.resolve("b").toString(), "--fail-on-warnings");
        Result clean = run("generate", "--model", writeModel().toString(), "--output", dir.resolve("c").toString(), "--fail-on-warnings");

        assertThat(lenient.exitCode()).isZero();
        assertThat(lenient.err()).contains("warning:").contains("Nowhere");
        assertThat(strict.exitCode()).isEqualTo(1);
        assertThat(strict.err()).contains("Nowhere").contains("--fail-on-warnings");
        assertThat(clean.exitCode()).isZero();
    }

    @Test
    void generateRejectsAnInvalidBaseUrl() throws IOException {
        Result result = run("generate", "--model", writeModel().toString(), "--output", dir.resolve("site").toString(), "--base-url", "not a url");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.err()).contains("base URL");
    }

    @Test
    void initCanCreateAGithubPagesWorkflow() throws IOException {
        Path project = dir.resolve("lib");
        Files.createDirectories(project);

        Result init = run("init", project.toString(), "--github-pages");
        String workflow = Files.readString(project.resolve(".github/workflows/docs.yml"));

        assertThat(init.exitCode()).isZero();
        assertThat(workflow).contains("actions/deploy-pages").contains("actions/upload-pages-artifact").contains("--github-pages");
        assertThat(Files.readString(project.resolve("doctize.yml"))).contains("githubPages: true").contains("# repository: ");
        assertThat(run("init", dir.resolve("plain").toString()).exitCode()).isZero();
        assertThat(dir.resolve("plain/.github")).doesNotExist();

        Result generate = run("generate", "--config", project.resolve("doctize.yml").toString(), "--output", project.resolve("out").toString());
        assertThat(generate.exitCode()).isZero();
        assertThat(generate.err()).contains("address of the site is unknown");
        assertThat(project.resolve("out/.nojekyll")).exists();
    }

    @Test
    void initRefusesToOverwriteAnExistingConfig() throws IOException {
        Files.writeString(dir.resolve("doctize.yml"), "project: {name: keep}");

        Result result = run("init", dir.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("doctize.yml"))).contains("keep");
    }

    private Path garbageLicense() throws IOException {
        Path file = dir.resolve("garbage.license");
        Files.writeString(file, "this is not a license\n");
        return file;
    }

    @Test
    void theLicenseCommandExplainsALicenseThatCannotBeUsed() throws IOException {
        Result result = run("license", "--license", garbageLicense().toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.out()).contains("License from the file", "Not usable:", "not a Doctize license");
    }

    @Test
    void theLicenseCommandReportsAMissingFile() {
        Result result = run("license", "--license", dir.resolve("missing.license").toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.err()).contains("does not exist");
    }

    @Test
    void generateFailsForALicenseFileThatDoesNotExist() throws IOException {
        Result result = run("generate", "--model", writeModel().toString(), "--output", dir.resolve("site").toString(),
                "--license", dir.resolve("missing.license").toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.err()).contains("does not exist");
    }

    @Test
    void theArchitectureMapWithoutAUsableLicenseIsSkippedWithAWarningAndTheSiteIsStillBuilt() throws IOException {
        Path site = dir.resolve("site");

        Result result = run("generate", "--model", writeModel().toString(), "--output", site.toString(), "--architecture",
                "--license", garbageLicense().toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.err()).contains("warning: The architecture diagrams were skipped.", "cannot be used");
        assertThat(site.resolve("index.html")).exists();
        assertThat(site.resolve("architecture")).doesNotExist();
    }

    @Test
    void failOnWarningsMakesAMissingLicenseAFailureForCi() throws IOException {
        Result result = run("generate", "--model", writeModel().toString(), "--output", dir.resolve("site").toString(), "--architecture",
                "--license", garbageLicense().toString(), "--fail-on-warnings");

        assertThat(result.exitCode()).isEqualTo(1);
    }

    @Test
    void theHelpOfGenerateListsTheLicenseAndArchitectureOptions() {
        assertThat(run("generate", "--help").out()).contains("--license", "--architecture", "--classes", "--diagram");
    }

    @Test
    void aKindOfDiagramCanBeSwitchedOnByNameInAnyCaseAndWithoutALicenseIsSkippedWithAWarning() throws IOException {
        Path site = dir.resolve("site");

        Result result = run("generate", "--model", writeModel().toString(), "--output", site.toString(), "--diagram", "Classes", "--diagram", "states",
                "--license", garbageLicense().toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.err()).contains("warning: The architecture diagrams were skipped.", "cannot be used");
        assertThat(site.resolve("architecture")).doesNotExist();
    }

    @Test
    void anUnknownKindOfDiagramIsRefusedWithTheKnownOnes() throws IOException {
        Result result = run("generate", "--model", writeModel().toString(), "--output", dir.resolve("site").toString(), "--diagram", "sequence");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.err()).contains("diagram type must be one of overview, classes, states, entities, flows", "sequence");
    }
}
