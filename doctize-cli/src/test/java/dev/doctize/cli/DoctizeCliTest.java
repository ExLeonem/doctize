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
    void initRefusesToOverwriteAnExistingConfig() throws IOException {
        Files.writeString(dir.resolve("doctize.yml"), "project: {name: keep}");

        Result result = run("init", dir.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("doctize.yml"))).contains("keep");
    }
}
