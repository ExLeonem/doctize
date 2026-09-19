package dev.doctize.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.doctize.DoctizeException;
import dev.doctize.annotations.Doc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads {@link DoctizeConfig} from YAML. */
@Doc(group = "Configuration", order = 25)
public final class ConfigLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private ConfigLoader() {}

    public static DoctizeConfig load(Path file) {
        try {
            String text = Files.readString(file);
            return text.isBlank() ? DoctizeConfig.defaults() : YAML.readValue(text, DoctizeConfig.class);
        } catch (JsonProcessingException e) {
            throw new DoctizeException("Invalid configuration " + file + ": " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new DoctizeException("Cannot read configuration " + file + ": " + e.getMessage(), e);
        }
    }
}
