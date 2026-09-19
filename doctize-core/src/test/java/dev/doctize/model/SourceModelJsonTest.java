package dev.doctize.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.doctize.DoctizeException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceModelJsonTest {

    @TempDir
    Path dir;

    static SourceModel sample() {
        Declaration method = new Declaration(
                ElementKind.METHOD, "get", "a.B.get", "a", "String get(int i)", List.of("@Deprecated"),
                List.of(new Parameter("i", "int")), "String", List.of("IOException"));
        Declaration type = new Declaration(ElementKind.CLASS, "B", "a.B", "a", "class B", List.of(), List.of(), null, List.of());
        SourceElement member = new SourceElement(Role.API, method, "Gets.", Placement.none(), List.of(), List.of());
        return SourceModel.of(List.of(new SourceElement(
                Role.API, type, "A B.", new Placement("Sec", "Title", 3, "g"), List.of("k"), List.of(member))));
    }

    @Test
    void roundTrips() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SourceModelJson.write(sample(), out);

        assertThat(SourceModelJson.read(new ByteArrayInputStream(out.toByteArray()))).isEqualTo(sample());
    }

    @Test
    void toleratesMissingAndUnknownFields() throws IOException {
        String json = "{\"formatVersion\":1,\"future\":true,\"elements\":[{\"role\":\"PAGE\",\"declaration\":{\"kind\":\"PACKAGE\",\"name\":\"p\"}}]}";

        SourceModel model = SourceModelJson.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(model.elements().getFirst().members()).isEmpty();
        assertThat(model.elements().getFirst().placement().order()).isEqualTo(Placement.DEFAULT_ORDER);
    }

    @Test
    void rejectsModelsFromNewerVersions() {
        byte[] json = "{\"formatVersion\":99,\"elements\":[]}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> SourceModelJson.read(new ByteArrayInputStream(json)))
                .isInstanceOf(DoctizeException.class)
                .hasMessageContaining("newer");
    }

    @Test
    void locatorFindsTheModelInDirectoriesAndFiles() throws IOException {
        Path model = dir.resolve(SourceModelJson.RESOURCE_PATH);
        Files.createDirectories(model.getParent());
        try (var out = Files.newOutputStream(model)) {
            SourceModelJson.write(sample(), out);
        }

        assertThat(ModelLocator.load(dir)).isEqualTo(sample());
        assertThat(ModelLocator.load(model)).isEqualTo(sample());
        assertThatThrownBy(() -> ModelLocator.load(dir.resolve("empty"))).isInstanceOf(DoctizeException.class);
    }
}
