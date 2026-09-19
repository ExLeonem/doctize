package dev.doctize.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.doctize.DoctizeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Reads and writes the {@link SourceModel} as JSON, the hand-over format between processor and generator. */
public final class SourceModelJson {

    /** Location of the model inside a class output directory or jar. */
    public static final String RESOURCE_PATH = "META-INF/doctize/model.json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private SourceModelJson() {}

    public static void write(SourceModel model, OutputStream out) throws IOException {
        MAPPER.writeValue(out, model);
    }

    public static SourceModel read(InputStream in) throws IOException {
        SourceModel model = MAPPER.readValue(in, SourceModel.class);
        if (model.formatVersion() > SourceModel.CURRENT_VERSION) {
            throw new DoctizeException("The documentation model was written by a newer Doctize version (format "
                    + model.formatVersion() + "); please upgrade the generator.");
        }
        return model;
    }
}
