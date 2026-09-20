package dev.doctize.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.doctize.Doctize;
import dev.doctize.architecture.ArchitectureAnalyzer;
import dev.doctize.architecture.ArchitectureModel;
import dev.doctize.config.ArchitectureSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The guide "Architecture of Doctize" describes the packages of the generator, as a table and as a diagram. Documentation that is
 * written by hand goes out of date, so this test analyzes the code and compares: if a dependency moves, it says which line of the guide to
 * change.
 */
class ArchitectureGuideIntegrationTest {

    static final String GUIDE = "architecture-of-doctize.md";
    static String guide;
    static ArchitectureModel model;

    @BeforeAll
    static void readTheGuideAndAnalyzeTheCode() throws Exception {
        guide = Files.readString(Path.of(System.getProperty("doctize.docs.architecture")).resolve(GUIDE));
        // The way the guide says it is made: the root package is left out, and the cycles are looked for.
        ArchitectureSettings settings = ArchitectureSettings.fromConfig(true, null, null, null, "dev.doctize", null, null, List.of("dev.doctize"),
                null, null, null, true, false, null, null, null);
        Path core = Path.of(Doctize.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        model = new ArchitectureAnalyzer(settings).analyze(List.of(core));
    }

    /** What the code says: package to the packages it depends on. */
    private static Map<String, Set<String>> actual() {
        Map<String, Set<String>> dependencies = new TreeMap<>();
        model.nodes().forEach(node -> dependencies.put(node.name(), new TreeSet<>()));
        model.edges().forEach(edge -> dependencies.get(edge.from()).add(edge.to()));
        return dependencies;
    }

    @Test
    void thePackageTableSaysWhatTheCodeSays() {
        Map<String, Set<String>> documented = new TreeMap<>();
        Matcher row = Pattern.compile("(?m)^\\| `([a-z]+)` \\|[^|\\n]*\\| ([^|\\n]+) \\|$").matcher(tableOfPackages());
        while (row.find()) {
            Set<String> dependsOn = new TreeSet<>();
            Matcher name = Pattern.compile("`([a-z]+)`").matcher(row.group(2));
            while (name.find()) {
                dependsOn.add(name.group(1));
            }
            documented.put(row.group(1), dependsOn);
        }

        assertThat(documented.keySet()).as("the packages of the table in " + GUIDE).isEqualTo(actual().keySet());
        actual().forEach((pkg, dependsOn) -> assertThat(documented.get(pkg))
                .as("`%s` depends on this in the table of %s and on %s in the code: update the table", pkg, GUIDE, dependsOn)
                .isEqualTo(dependsOn));
    }

    private static String tableOfPackages() {
        int start = guide.indexOf("| Package | What it does | Depends on |");
        assertThat(start).as("the table of packages in " + GUIDE).isNotNegative();
        int end = guide.indexOf("\n\n", start);
        return guide.substring(start, end);
    }

    private static String diagram() {
        Matcher fence = Pattern.compile("```mermaid title=\"The packages of doctize-core\"\\n(.*?)\\n```", Pattern.DOTALL).matcher(guide);
        assertThat(fence.find()).as("the diagram \"The packages of doctize-core\" in " + GUIDE).isTrue();
        return fence.group(1);
    }

    @Test
    void theDiagramHasTheSamePackagesArrowsAndRedPackagesAsTheCode() {
        String source = diagram();
        Map<String, String> names = new HashMap<>();
        Matcher node = Pattern.compile("(?m)^\\s+(n\\d+)\\[\"<b>([a-z]+)</b>").matcher(source);
        while (node.find()) {
            names.put(node.group(1), node.group(2));
        }
        Map<String, Set<String>> documented = new TreeMap<>();
        names.values().forEach(name -> documented.put(name, new TreeSet<>()));
        Matcher arrow = Pattern.compile("(?m)^\\s+(n\\d+) -->(?:\\|\\d+\\| | )(n\\d+)$").matcher(source);
        while (arrow.find()) {
            documented.get(names.get(arrow.group(1))).add(names.get(arrow.group(2)));
        }
        Matcher red = Pattern.compile("(?m)^\\s+class ([n\\d,]+) cycle$").matcher(source);
        Set<String> documentedCycle = new LinkedHashSet<>();
        if (red.find()) {
            for (String id : red.group(1).split(",")) {
                documentedCycle.add(names.get(id));
            }
        }

        assertThat(documented.keySet()).as("the packages of the diagram in " + GUIDE).isEqualTo(actual().keySet());
        actual().forEach((pkg, dependsOn) -> assertThat(documented.get(pkg))
                .as("`%s` has these arrows in the diagram of %s and depends on %s in the code: generate the diagram again", pkg, GUIDE, dependsOn)
                .isEqualTo(dependsOn));
        assertThat(documentedCycle).as("the red packages of the diagram").isEqualTo(new TreeSet<>(model.cycles().stream().flatMap(List::stream).collect(Collectors.toSet())));
    }

    @Test
    void theCycleTheGuideDescribesIsTheOneTheCodeHas() {
        assertThat(model.cycles()).as("if the cycle is gone or has moved, rewrite the section \"The cycle\" of " + GUIDE).hasSize(1);
        String section = guide.substring(guide.indexOf("### The cycle"));
        for (String member : model.cycles().get(0)) {
            assertThat(section).as("the section 'The cycle' names " + member).contains("`" + member + "`");
        }
    }

    @Test
    void theRulesTheGuideStatesAreTheOnesTheCodeFollows() {
        Map<String, Set<String>> dependencies = actual();

        for (String independent : List.of("text", "model", "license")) {
            assertThat(dependencies.get(independent)).as("the guide says `%s` depends on no other package", independent).isEmpty();
        }
        assertThat(guide).contains("`text`, `model` and `license` depend on no other package");
    }
}
