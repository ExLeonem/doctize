package dev.doctize.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.doctize.testing.Processes;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Java produces the search index, JavaScript reads it. This test runs the two together: the index of a generated
 * site is loaded into the generated search engine, in Node.js, and real queries are answered. It is skipped
 * when Node.js is not installed.
 */
class SearchEngineIntegrationTest {

    @TempDir
    static Path work;

    static Path site;
    static Path runner;

    @BeforeAll
    static void generate() throws URISyntaxException {
        assumeTrue(nodeIsInstalled(), "Node.js is not installed");
        Path project = SampleProject.copy("sample-shop", work);
        site = work.resolve("site");
        SampleProject.generate(project, SampleProject.compile(project), site, builder -> builder);
        runner = Path.of(SearchEngineIntegrationTest.class.getResource("/search-runner.js").toURI());
    }

    private static boolean nodeIsInstalled() {
        try {
            return Processes.run(List.of("node", "--version"), Map.of(), work, Duration.ofSeconds(20)).exitCode() == 0;
        } catch (RuntimeException | AssertionError e) {
            return false;
        }
    }

    private static Map<String, List<String>> search(String... queries) throws IOException {
        List<String> command = new java.util.ArrayList<>(List.of("node", runner.toString(), site.toString()));
        command.addAll(List.of(queries));
        Processes.Result result = Processes.run(command, Map.of(), work.resolve("node"), Duration.ofSeconds(60));
        assertThat(result.exitCode()).as(result.toString()).isZero();
        return new ObjectMapper().readValue(result.out(), new TypeReference<>() {});
    }

    @Test
    void findsMembersTypesGuidesAndKeywords() throws IOException {
        Map<String, List<String>> answers = search("subtotal", "basket", "shipped", "install", "pricing rules", "add", "pieces");

        assertThat(answers.get("subtotal").getFirst()).startsWith("reference/shop.Cart.Line.html#method-subtotal");
        assertThat(answers.get("basket").getFirst()).isEqualTo("reference/shop.Cart.html");
        assertThat(answers.get("shipped").getFirst()).startsWith("reference/shop.Status.html");
        assertThat(answers.get("install")).as("the section headed \"Install\" and the page \"Installation\"")
                .contains("quick-start/start.html#install").anyMatch(url -> url.startsWith("setup/install.html"));
        assertThat(answers.get("pricing rules").getFirst()).startsWith("guides/pricing-rules.html");
        assertThat(answers.get("add")).anyMatch(url -> url.startsWith("reference/shop.Cart.html#method-add-Item-int"));
        assertThat(answers.get("pieces")).as("API descriptions are not searched").noneMatch(url -> url.startsWith("reference/"));
    }

    @Test
    void toleratesTyposAndMatchesPrefixesAndCamelCaseParts() throws IOException {
        Map<String, List<String>> answers = search("subtotl", "chekout", "befor", "rebuild");

        assertThat(answers.get("subtotl").getFirst()).startsWith("reference/shop.Cart.Line.html");
        assertThat(answers.get("chekout").getFirst()).isEqualTo("reference/shop.Cart.html");
        assertThat(answers.get("befor")).anyMatch(url -> url.startsWith("reference/shop.Pricing.html#method-beforeTaxes"));
        assertThat(answers.get("rebuild")).as("@DocIgnore members are not searchable").isEmpty();
    }
}
