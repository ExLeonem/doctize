package dev.doctize.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectInfoTest {

    private final ProjectInfo fromBuild = new ProjectInfo("acme-cache", "A cache.", "1.2.0", "https://github.com/acme/cache", "Built.");

    @Test
    void valuesThatAreNotSetComeFromTheFallback() {
        ProjectInfo project = new ProjectInfo(null, null, null, null, null).orElse(fromBuild);

        assertThat(project).isEqualTo(fromBuild);
    }

    @Test
    void valuesThatAreSetWinOverTheFallback() {
        ProjectInfo configured = new ProjectInfo("Acme Cache", null, "2.0.0", null, null);

        ProjectInfo project = configured.orElse(fromBuild);

        assertThat(project.name()).isEqualTo("Acme Cache");
        assertThat(project.version()).isEqualTo("2.0.0");
        assertThat(project.tagline()).isEqualTo("A cache.");
    }

    @Test
    void withoutAnyNameTheProjectIsCalledDocumentation() {
        ProjectInfo project = new ProjectInfo(null, null, null, null, null).orElse(new ProjectInfo(null, null, null, null, null));

        assertThat(project.name()).isEqualTo(ProjectInfo.DEFAULT_NAME);
    }
}
