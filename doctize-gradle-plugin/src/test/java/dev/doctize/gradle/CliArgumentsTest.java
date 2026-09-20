package dev.doctize.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliArgumentsTest {

    @Test
    void aBareCallOnlyNamesTheCommand() {
        assertThat(CliArguments.generate().build()).containsExactly("generate");
    }

    @Test
    void unsetValuesAndDisabledFlagsAreLeftOut() {
        List<String> arguments = CliArguments.generate()
                .option("--config", (File) null)
                .option("--base-url", (String) null)
                .option("--cname", " ")
                .flag("--github-pages", false)
                .build();

        assertThat(arguments).containsExactly("generate");
    }

    @Test
    void everyModelGetsItsOwnOption() {
        List<String> arguments = CliArguments.generate()
                .options("--model", List.of(new File("core/classes"), new File("client/classes")))
                .build();

        assertThat(arguments).containsExactly("generate", "--model", new File("core/classes").getPath(), "--model", new File("client/classes").getPath());
    }

    @Test
    void extraArgumentsComeLastSoTheyCanOverrideEarlierOnes() {
        List<String> arguments = CliArguments.generate()
                .option("--output", new File("build/docs"))
                .flag("--fail-on-warnings", true)
                .append(List.of("--base-url", "https://acme.github.io/cache/"))
                .build();

        assertThat(arguments).containsExactly(
                "generate", "--output", new File("build/docs").getPath(), "--fail-on-warnings", "--base-url", "https://acme.github.io/cache/");
    }
}
