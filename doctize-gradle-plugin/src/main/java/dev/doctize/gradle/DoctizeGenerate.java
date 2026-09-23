package dev.doctize.gradle;

import java.io.File;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Runs {@code doctize generate}. The tool needs Java 25, which Gradle itself does not, so it runs in a separate
 * process on the launcher of a toolchain rather than inside the build.
 */
@DisableCachingByDefault(because = "The site is cheap to generate, and the tool runs in a separate process")
public abstract class DoctizeGenerate extends DefaultTask {

    private static final String MAIN_CLASS = "dev.doctize.cli.DoctizeCli";
    private static final String MODEL_PATH = "META-INF/doctize/model.json";

    /** The {@code doctize-cli} and its dependencies. */
    @Classpath
    public abstract ConfigurableFileCollection getToolClasspath();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getModels();

    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getConfig();

    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getGuides();

    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getTheme();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAdditionalInputs();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @Input
    @Option(option = "github-pages", description = "Prepares the output for GitHub Pages (.nojekyll, CNAME).")
    public abstract Property<Boolean> getGithubPages();

    @Optional
    @Input
    @Option(option = "base-url", description = "The address the site will be published at, e.g. https://acme.github.io/project/.")
    public abstract Property<String> getBaseUrl();

    @Optional
    @Input
    @Option(option = "custom-domain", description = "The custom domain of a GitHub Pages site, e.g. docs.acme.dev.")
    public abstract Property<String> getCustomDomain();

    /** The name of the site, unless {@code project.name} of the configuration says otherwise. Default: the name of the Gradle project. */
    @Optional
    @Input
    public abstract Property<String> getProjectName();

    /** Shown next to the name, unless {@code project.version} of the configuration says otherwise. Default: the version of the Gradle project. */
    @Optional
    @Input
    public abstract Property<String> getProjectVersion();

    /** The tagline on the home page, unless {@code project.tagline} of the configuration says otherwise. Default: the description of the Gradle project. */
    @Optional
    @Input
    public abstract Property<String> getProjectTagline();

    @Input
    @Option(option = "fail-on-warnings", description = "Fails the build if generating the site produced warnings.")
    public abstract Property<Boolean> getFailOnWarnings();

    @Input
    public abstract ListProperty<String> getArguments();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract ProjectLayout getLayout();

    @TaskAction
    void generate() {
        List<File> models = modelsWithContent();
        List<String> arguments = CliArguments.generate()
                .option("--config", getConfig().getAsFile().getOrNull())
                .options("--model", models)
                .option("--guides", getGuides().getAsFile().getOrNull())
                .option("--theme", getTheme().getAsFile().getOrNull())
                .option("--output", getOutput().get().getAsFile())
                .flag("--github-pages", getGithubPages().get())
                .option("--base-url", getBaseUrl().getOrNull())
                .option("--cname", getCustomDomain().getOrNull())
                .option("--project-name", getProjectName().getOrNull())
                .option("--project-version", getProjectVersion().getOrNull())
                .option("--project-tagline", getProjectTagline().getOrNull())
                .flag("--fail-on-warnings", getFailOnWarnings().get())
                .append(getArguments().get())
                .build();

        int exitValue = getExecOperations().javaexec(spec -> {
            spec.setExecutable(getJavaLauncher().get().getExecutablePath().getAsFile().getPath());
            spec.setClasspath(getToolClasspath());
            spec.getMainClass().set(MAIN_CLASS);
            spec.setWorkingDir(getLayout().getProjectDirectory().getAsFile());
            spec.args(arguments);
            spec.setIgnoreExitValue(true);
        }).getExitValue();

        if (exitValue != 0) {
            throw new GradleException("doctize generate failed with exit value " + exitValue + "; its output is above.");
        }
    }

    /**
     * A class directory only has a model if it contains an annotated type, so directories without one are left out
     * instead of failing the tool: a Kotlin output directory, or a module that only has guides.
     */
    private List<File> modelsWithContent() {
        List<File> candidates = getModels().getFiles().stream().filter(File::exists).toList();
        List<File> withModel = candidates.stream()
                .filter(file -> !file.isDirectory() || new File(file, MODEL_PATH).isFile())
                .toList();
        if (withModel.isEmpty() && !getModels().isEmpty()) {
            getLogger().warn("No documentation model ({}) was found in {}, so the site has no API reference. Annotate types with @Doc,"
                    + " and check that doctize-processor is on the annotationProcessor path.", MODEL_PATH, getModels().getFiles());
        }
        return withModel;
    }
}
