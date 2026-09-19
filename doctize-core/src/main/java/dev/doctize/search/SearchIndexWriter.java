package dev.doctize.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.doctize.DoctizeException;
import java.util.List;
import java.util.Map;

/**
 * Serializes the search records as a script that defines {@code window.DOCTIZE_SEARCH_INDEX}.
 * A script (instead of a JSON file that is fetched) keeps search working when the site is opened
 * straight from the file system, where browsers block {@code fetch}.
 */
public final class SearchIndexWriter {

    public static final String FILE_NAME = "search-index.js";

    private static final ObjectMapper JSON = new ObjectMapper();

    private SearchIndexWriter() {}

    public static String toScript(List<SearchRecord> records) {
        try {
            String json = JSON.writeValueAsString(Map.of("version", 1, "records", records));
            return "window.DOCTIZE_SEARCH_INDEX = " + json.replace("</", "<\\/") + ";\n";
        } catch (JsonProcessingException e) {
            throw new DoctizeException("Cannot serialize the search index: " + e.getMessage(), e);
        }
    }
}
