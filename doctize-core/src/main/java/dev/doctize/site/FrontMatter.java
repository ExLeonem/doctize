package dev.doctize.site;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.doctize.DoctizeException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Markdown file split into its YAML front matter and its body.
 *
 * <pre>
 * ---
 * title: Quick start
 * order: 1
 * ---
 * </pre>
 */
record FrontMatter(Map<String, Object> values, String body) {

    private static final Pattern BLOCK = Pattern.compile("\\A---[ \\t]*\\R(?:(.*?)\\R)?---[ \\t]*(?:\\R|\\z)", Pattern.DOTALL);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    static FrontMatter parse(String text, String fileName) {
        Matcher matcher = BLOCK.matcher(text);
        if (!matcher.find()) {
            return new FrontMatter(Map.of(), text);
        }
        try {
            String yaml = matcher.group(1) == null ? "" : matcher.group(1);
            Map<String, Object> values = yaml.isBlank() ? null : YAML.readValue(yaml, new TypeReference<>() {});
            return new FrontMatter(values == null ? Map.of() : values, text.substring(matcher.end()));
        } catch (JsonProcessingException e) {
            throw new DoctizeException("Invalid front matter in " + fileName + ": " + e.getOriginalMessage(), e);
        }
    }

    String string(String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    int integer(String key, int fallback) {
        return values.get(key) instanceof Number number ? number.intValue() : fallback;
    }

    List<String> strings(String key) {
        return switch (values.get(key)) {
            case List<?> list -> list.stream().map(Object::toString).toList();
            case String single -> List.of(single);
            case null, default -> List.of();
        };
    }
}
