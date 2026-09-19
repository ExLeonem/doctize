package dev.doctize.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.DoctizeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputDirectoryTest {

    @TempDir
    Path dir;

    @Test
    void prepareCreatesTheDirectoryWithAMarker() {
        OutputDirectory output = new OutputDirectory(dir.resolve("site/nested"));

        output.prepare();

        assertThat(dir.resolve("site/nested/.doctize-site")).exists();
    }

    @Test
    void writesCreateMissingParentDirectories() throws IOException {
        OutputDirectory output = new OutputDirectory(dir);
        output.prepare();

        output.write("a/b/c.html", "text");
        output.write("bin.dat", new byte[] {1, 2, 3});

        assertThat(Files.readString(dir.resolve("a/b/c.html"))).isEqualTo("text");
        assertThat(Files.readAllBytes(dir.resolve("bin.dat"))).containsExactly(1, 2, 3);
    }

    @Test
    void copyTakesTheContentOfAFile() throws IOException {
        Path source = Files.writeString(dir.resolve("source.txt"), "copied");
        OutputDirectory output = new OutputDirectory(dir.resolve("out"));
        output.prepare();

        output.copy("x/target.txt", source);

        assertThat(Files.readString(dir.resolve("out/x/target.txt"))).isEqualTo("copied");
    }

    @Test
    void preparingAgainRemovesOldOutputOfADirectoryItCreated() throws IOException {
        OutputDirectory output = new OutputDirectory(dir.resolve("out"));
        output.prepare();
        output.write("old/page.html", "old");

        output.prepare();

        assertThat(dir.resolve("out/old")).doesNotExist();
        assertThat(dir.resolve("out/.doctize-site")).exists();
    }

    @Test
    void neverDeletesInADirectoryItDidNotCreate() throws IOException {
        Path foreign = Files.createDirectories(dir.resolve("foreign"));
        Files.writeString(foreign.resolve("precious.txt"), "keep");
        Files.createDirectories(foreign.resolve("sub"));
        Files.writeString(foreign.resolve("sub/also.txt"), "keep");

        new OutputDirectory(foreign).prepare();

        assertThat(Files.readString(foreign.resolve("precious.txt"))).isEqualTo("keep");
        assertThat(foreign.resolve("sub/also.txt")).exists();
    }

    @Test
    void refusesToWriteOutsideOfTheOutputDirectory() {
        OutputDirectory output = new OutputDirectory(dir.resolve("out"));
        output.prepare();

        assertThatThrownBy(() -> output.write("../escaped.txt", "x")).isInstanceOf(DoctizeException.class).hasMessageContaining("outside");
        assertThatThrownBy(() -> output.write("a/../../escaped.txt", "x")).isInstanceOf(DoctizeException.class);
        assertThat(dir.resolve("escaped.txt")).doesNotExist();
    }

    @Test
    void copyOfAMissingFileIsReported() {
        OutputDirectory output = new OutputDirectory(dir.resolve("out"));
        output.prepare();

        assertThatThrownBy(() -> output.copy("x.txt", dir.resolve("missing.txt"))).isInstanceOf(DoctizeException.class).hasMessageContaining("missing.txt");
    }
}
