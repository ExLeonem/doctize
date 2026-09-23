package dev.doctize.gradle;

import java.util.List;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * The {@code dev.doctize} plugin: registers the {@code doctize { }} block and the {@code generateDocs} task, and in a
 * Java project puts the annotations and the annotation processor on the right class paths.
 */
public class DoctizePlugin implements Plugin<Project> {

    static final String EXTENSION = "doctize";
    static final String TASK = "generateDocs";
    static final String TOOL_CONFIGURATION = "doctizeTool";
    /** The tool is compiled for this Java release. */
    static final int TOOL_JAVA_VERSION = 25;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final JavaToolchainService toolchains;

    @Inject
    public DoctizePlugin(JavaToolchainService toolchains) {
        this.toolchains = toolchains;
    }

    @Override
    public void apply(Project project) {
        DoctizeExtension doctize = project.getExtensions().create(EXTENSION, DoctizeExtension.class);
        setDefaults(project, doctize);

        var tool = project.getConfigurations().create(TOOL_CONFIGURATION, configuration -> {
            configuration.setDescription("The Doctize command line tool that generateDocs runs.");
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
        });
        project.getDependencies().addProvider(TOOL_CONFIGURATION, doctize.getVersion().map(version -> artifact("doctize-cli", version)));

        project.getPlugins().withType(JavaPlugin.class, java -> {
            var main = project.getExtensions().getByType(SourceSetContainer.class).getByName("main");
            doctize.getModels().convention(main.getOutput().getClassesDirs());
            addWhenEnabled(project, doctize, "compileOnly", "doctize-annotations");
            addWhenEnabled(project, doctize, "annotationProcessor", "doctize-processor");
        });

        project.getTasks().register(TASK, DoctizeGenerate.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generates the documentation site with Doctize.");
            task.getToolClasspath().from(tool);
            task.getModels().from(doctize.getModels());
            task.getConfig().convention(doctize.getConfig());
            task.getGuides().convention(doctize.getGuides());
            task.getTheme().convention(doctize.getTheme());
            task.getAdditionalInputs().from(doctize.getAdditionalInputs());
            task.getOutput().convention(doctize.getOutput());
            task.getGithubPages().convention(doctize.getGithubPages());
            task.getBaseUrl().convention(doctize.getBaseUrl());
            task.getCustomDomain().convention(doctize.getCustomDomain());
            task.getFailOnWarnings().convention(doctize.getFailOnWarnings());
            // The root project names the product; a documentation project of its own is often just called "docs" or "site".
            task.getProjectName().convention(project.getRootProject().getName());
            task.getProjectVersion().convention(project.provider(() -> firstNonNull(version(project), version(project.getRootProject()))));
            task.getProjectTagline().convention(project.provider(() -> firstNonNull(project.getDescription(), project.getRootProject().getDescription())));
            task.getArguments().convention(doctize.getArguments());
            task.getJavaLauncher().convention(doctize.getJavaLauncher());
        });
    }

    private void setDefaults(Project project, DoctizeExtension doctize) {
        ProjectLayout layout = project.getLayout();
        ProviderFactory providers = project.getProviders();

        doctize.getVersion().convention(PluginVersion.get());
        doctize.getAddDependencies().convention(true);
        // Only when they exist: a missing input directory fails the task, and a project may have no guides at all.
        doctize.getConfig().convention(providers.provider(() -> {
            RegularFile file = layout.getProjectDirectory().file("doctize.yml");
            return file.getAsFile().isFile() ? file : null;
        }));
        doctize.getGuides().convention(providers.provider(() -> {
            Directory directory = layout.getProjectDirectory().dir("docs");
            return directory.getAsFile().isDirectory() ? directory : null;
        }));
        doctize.getOutput().convention(layout.getBuildDirectory().dir("docs"));
        doctize.getGithubPages().convention(false);
        doctize.getFailOnWarnings().convention(false);
        // The scaffold of `doctize init --github-pages` passes the address of the site this way.
        doctize.getArguments().convention(providers.gradleProperty("doctizeArgs")
                .map(DoctizePlugin::splitArguments)
                .orElse(List.of()));
        doctize.getJavaLauncher().convention(toolchains.launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(TOOL_JAVA_VERSION))));
    }

    /**
     * The setting is read when the configuration is used, so {@code doctize { addDependencies = false }} works after the plugin
     * is applied. The provider always has a value (an empty list when disabled): a dependency provider without one is still
     * counted by the dependency set, but never yields a dependency.
     */
    private static void addWhenEnabled(Project project, DoctizeExtension doctize, String configuration, String module) {
        Provider<List<Dependency>> dependencies = doctize.getAddDependencies().zip(doctize.getVersion(),
                (enabled, version) -> enabled ? List.of(project.getDependencies().create(artifact(module, version))) : List.of());
        project.getConfigurations().getByName(configuration).getDependencies().addAllLater(dependencies);
    }

    /** The version of the project, which Gradle calls {@code unspecified} when none is set. */
    private static String version(Project project) {
        String version = String.valueOf(project.getVersion());
        return version.equals(Project.DEFAULT_VERSION) ? null : version;
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    /** Splits the value of the {@code doctizeArgs} project property at whitespace. */
    static List<String> splitArguments(String value) {
        return value.isBlank() ? List.of() : List.of(WHITESPACE.split(value.trim()));
    }

    private static String artifact(String module, String version) {
        return "dev.doctize:" + module + ":" + version;
    }
}
