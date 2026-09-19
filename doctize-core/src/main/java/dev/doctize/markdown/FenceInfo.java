package dev.doctize.markdown;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The info string of a fenced code block: a language followed by {@code key="value"} attributes,
 * e.g. {@code java title="Cache.java"}. The {@code tab} attribute groups consecutive blocks into tabs.
 */
public record FenceInfo(String language, Map<String, String> attributes) {

    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)=(?:\"([^\"]*)\"|(\\S+))");

    public static FenceInfo parse(String info) {
        String text = info == null ? "" : info.strip();
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(text);
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        }
        String remainder = matcher.replaceAll("").strip();
        String language = remainder.isEmpty() ? "" : remainder.split("\\s+")[0].toLowerCase();
        return new FenceInfo(language, Map.copyOf(attributes));
    }

    public String title() {
        return attributes.getOrDefault("title", "");
    }

    public String tab() {
        return attributes.getOrDefault("tab", "");
    }

    public boolean isTabbed() {
        return !tab().isEmpty();
    }
}
