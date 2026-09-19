package dev.doctize.config;

import dev.doctize.annotations.Doc;

/**
 * How the API reference is generated.
 *
 * @param section the section for types that do not name one
 * @param includeUndocumented whether members without a doc comment are listed
 */
@Doc(group = "Configuration", order = 24)
public record ApiSettings(String section, boolean includeUndocumented) {

    public static final String DEFAULT_SECTION = "API Reference";

    public ApiSettings {
        section = section == null || section.isBlank() ? DEFAULT_SECTION : section;
    }

    public static ApiSettings defaults() {
        return new ApiSettings(DEFAULT_SECTION, true);
    }
}
