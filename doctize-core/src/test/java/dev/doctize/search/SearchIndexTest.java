package dev.doctize.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchIndexTest {

    @Test
    void indexIsAScriptSoItWorksFromTheFileSystem() {
        String script = SearchIndexWriter.toScript(List.of(new SearchRecord("a.html#x", "T", "S", "H", "text </script> end", "k")));

        assertThat(script).startsWith("window.DOCTIZE_SEARCH_INDEX = {").endsWith(";\n");
        assertThat(script).contains("\"url\":\"a.html#x\"").doesNotContain("</script>");
    }
}
