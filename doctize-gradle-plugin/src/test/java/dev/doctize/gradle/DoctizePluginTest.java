package dev.doctize.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctizePluginTest {

    @TempDir
    Path directory;

    private Project project(String... plugins) {
        Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        for (String plugin : plugins) {
            project.getPluginManager().apply(plugin);
        }
        return project;
    }

    private static List<String> dependencies(Project project, String configuration) {
        return project.getConfigurations().getByName(configuration).getDependencies().stream()
                .map((Dependency dependency) -> dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion())
                .toList();
    }

    private static DoctizeGenerate task(Project project) {
        return (DoctizeGenerate) project.getTasks().getByName("generateDocs");
    }

    @Test
    void theGenerateDocsTaskIsInTheDocumentationGroup() {
        Project project = project("dev.doctize");

        assertThat(task(project).getGroup()).isEqualTo("documentation");
        assertThat(project.getExtensions().findByName("doctize")).isInstanceOf(DoctizeExtension.class);
    }

    @Test
    void theSiteIsNamedAfterTheBuild() {
        Project project = project("dev.doctize");
        project.setVersion("1.2.0");
        project.setDescription("A small cache.");

        assertThat(task(project).getProjectName().get()).isEqualTo(project.getName());
        assertThat(task(project).getProjectVersion().get()).isEqualTo("1.2.0");
        assertThat(task(project).getProjectTagline().get()).isEqualTo("A small cache.");
    }

    @Test
    void aProjectWithoutVersionOrDescriptionLeavesThemOut() {
        Project project = project("dev.doctize");

        assertThat(task(project).getProjectVersion().isPresent()).isFalse();
        assertThat(task(project).getProjectTagline().isPresent()).isFalse();
    }

    @Test
    void aDocumentationProjectInAMultiModuleBuildIsNamedAfterTheRootProject() {
        Project root = ProjectBuilder.builder().withName("acme-cache").withProjectDir(directory.toFile()).build();
        root.setVersion("2.0.0");
        Project site = ProjectBuilder.builder().withName("site").withParent(root).build();
        site.getPluginManager().apply("dev.doctize");

        assertThat(task(site).getProjectName().get()).isEqualTo("acme-cache");
        assertThat(task(site).getProjectVersion().get()).isEqualTo("2.0.0");
    }

    @Test
    void aJavaProjectGetsTheAnnotationsAndTheProcessorInTheVersionOfThePlugin() {
        Project project = project("java", "dev.doctize");
        String version = PluginVersion.get();

        assertThat(dependencies(project, "compileOnly")).containsExactly("dev.doctize:doctize-annotations:" + version);
        assertThat(dependencies(project, "annotationProcessor")).containsExactly("dev.doctize:doctize-processor:" + version);
        assertThat(dependencies(project, "doctizeTool")).containsExactly("dev.doctize:doctize-cli:" + version);
    }

    @Test
    void theOrderOfThePluginsDoesNotMatter() {
        Project project = project("dev.doctize", "java");

        assertThat(dependencies(project, "compileOnly")).hasSize(1);
        assertThat(dependencies(project, "annotationProcessor")).hasSize(1);
    }

    @Test
    void theVersionCanBeChosen() {
        Project project = project("java", "dev.doctize");
        project.getExtensions().getByType(DoctizeExtension.class).getVersion().set("9.9.9");

        assertThat(dependencies(project, "compileOnly")).containsExactly("dev.doctize:doctize-annotations:9.9.9");
        assertThat(dependencies(project, "doctizeTool")).containsExactly("dev.doctize:doctize-cli:9.9.9");
    }

    @Test
    void dependenciesCanBeLeftToTheBuild() {
        Project project = project("java", "dev.doctize");
        project.getExtensions().getByType(DoctizeExtension.class).getAddDependencies().set(false);

        assertThat(dependencies(project, "compileOnly")).isEmpty();
        assertThat(dependencies(project, "annotationProcessor")).isEmpty();
        assertThat(dependencies(project, "doctizeTool")).hasSize(1);
    }

    @Test
    void aProjectWithoutJavaGetsNoCompileDependenciesAndNoModels() {
        Project project = project("dev.doctize");

        assertThat(project.getConfigurations().findByName("compileOnly")).isNull();
        assertThat(task(project).getModels().getFiles()).isEmpty();
    }

    @Test
    void theModelsAreTheClassesOfTheMainSourceSet() {
        Project project = project("java", "dev.doctize");

        assertThat(task(project).getModels().getFiles())
                .containsExactly(project.getLayout().getBuildDirectory().dir("classes/java/main").get().getAsFile());
    }

    @Test
    void modulesAreDocumentedTogetherRegardlessOfTheOrderTheyAreConfiguredIn() {
        Project root = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        Project docs = ProjectBuilder.builder().withName("docs").withParent(root).build();
        Project core = ProjectBuilder.builder().withName("core").withParent(root).build();
        Project client = ProjectBuilder.builder().withName("client").withParent(root).build();
        docs.getPluginManager().apply("dev.doctize");
        docs.getExtensions().getByType(DoctizeExtension.class).modules(core, client);

        core.getPluginManager().apply("java");
        assertThat(task(docs).getModels().getFiles()).containsExactly(classesOf(core));
        client.getPluginManager().apply("java");
        assertThat(task(docs).getModels().getFiles()).containsExactlyInAnyOrder(classesOf(core), classesOf(client));
    }

    private static java.io.File classesOf(Project project) {
        return project.getLayout().getBuildDirectory().dir("classes/java/main").get().getAsFile();
    }

    @Test
    void theOutputDefaultsToBuildDocs() {
        Project project = project("dev.doctize");

        assertThat(task(project).getOutput().get().getAsFile()).isEqualTo(directory.resolve("build/docs").toFile());
    }

    @Test
    void configurationAndGuidesAreOnlyUsedWhenTheyExist() throws IOException {
        assertThat(task(project("dev.doctize")).getConfig().isPresent()).isFalse();
        assertThat(task(project("dev.doctize")).getGuides().isPresent()).isFalse();

        Files.writeString(directory.resolve("doctize.yml"), "project:\n  name: Acme\n");
        Files.createDirectories(directory.resolve("docs"));
        Project withFiles = project("dev.doctize");

        assertThat(task(withFiles).getConfig().get().getAsFile()).isEqualTo(directory.resolve("doctize.yml").toFile());
        assertThat(task(withFiles).getGuides().get().getAsFile()).isEqualTo(directory.resolve("docs").toFile());
    }

    @Test
    void settingsOfTheExtensionReachTheTask() {
        Project project = project("dev.doctize");
        DoctizeExtension doctize = project.getExtensions().getByType(DoctizeExtension.class);
        doctize.getGithubPages().set(true);
        doctize.getBaseUrl().set("https://acme.github.io/cache/");
        doctize.getFailOnWarnings().set(true);
        doctize.getOutput().set(project.getLayout().getProjectDirectory().dir("site"));

        DoctizeGenerate task = task(project);

        assertThat(task.getGithubPages().get()).isTrue();
        assertThat(task.getBaseUrl().get()).isEqualTo("https://acme.github.io/cache/");
        assertThat(task.getFailOnWarnings().get()).isTrue();
        assertThat(task.getOutput().get().getAsFile()).isEqualTo(directory.resolve("site").toFile());
    }

    @Test
    void theDoctizeArgsPropertyIsSplitAtWhitespace() {
        assertThat(DoctizePlugin.splitArguments("--github-pages  --base-url https://acme.github.io/cache/ "))
                .containsExactly("--github-pages", "--base-url", "https://acme.github.io/cache/");
        assertThat(DoctizePlugin.splitArguments("  ")).isEmpty();
    }

    @Test
    void withoutTheProjectPropertyThereAreNoExtraArguments() {
        assertThat(task(project("dev.doctize")).getArguments().get()).isEmpty();
    }

    @Test
    void theToolRunsOnAToolchainLauncher() {
        assertThat(task(project("dev.doctize")).getJavaLauncher().isPresent()).isTrue();
    }
}
