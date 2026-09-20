package dev.doctize.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.toolchain.JavaLauncher;

/**
 * The {@code doctize { }} block. Every setting has a default, so the block is only needed to change one.
 * The settings mirror the options of {@code doctize generate}; a value set here wins over the same setting in
 * {@code doctize.yml}.
 */
public abstract class DoctizeExtension {

    /** The version of Doctize to use for the tool and the processor. Default: the version of this plugin. */
    public abstract Property<String> getVersion();

    /** Adds the annotations ({@code compileOnly}) and the processor ({@code annotationProcessor}) to a Java project. Default: true. */
    public abstract Property<Boolean> getAddDependencies();

    /** The configuration file. Default: {@code doctize.yml} in the project directory, if it exists. */
    public abstract RegularFileProperty getConfig();

    /** The directory with the Markdown guides. Default: {@code docs} in the project directory, if it exists. */
    public abstract DirectoryProperty getGuides();

    /** A custom theme directory. Default: none, the built-in theme. */
    public abstract DirectoryProperty getTheme();

    /** Where the site is written. Default: {@code build/docs}. */
    public abstract DirectoryProperty getOutput();

    /**
     * Where the annotation processor left its output: class directories, {@code model.json} files or jars.
     * Default: the main source set of a Java project. Adding to this collection replaces the default, so list
     * the main source set as well to document it together with other modules.
     */
    public abstract ConfigurableFileCollection getModels();

    /**
     * Documents the Java modules (projects) it is given, in one site. Their main source sets are added to {@link #getModels()},
     * so, like anything added there, they replace the default: name this project too if it has code of its own. The order in which
     * the projects are configured does not matter.
     */
    public void modules(Project... projects) {
        for (Project project : projects) {
            project.getPlugins().withType(JavaPlugin.class, java ->
                    getModels().from(project.getExtensions().getByType(SourceSetContainer.class).getByName("main").getOutput().getClassesDirs()));
        }
    }

    /** Files the configuration reads that are not named by another setting, e.g. {@code theme.extraCss}. They make the task run again when they change. */
    public abstract ConfigurableFileCollection getAdditionalInputs();

    /**
     * The license file, which unlocks licensed features such as the architecture map. Default: {@code doctize.license} in the
     * project directory if it exists; otherwise the tool looks at the environment variable {@code DOCTIZE_LICENSE} and at
     * {@code ~/.doctize/license}.
     */
    public abstract RegularFileProperty getLicense();

    /** Generates the architecture map, a licensed feature. Default: false; {@code architecture.enabled} in {@code doctize.yml} does the same. */
    public abstract Property<Boolean> getArchitecture();

    /** Prepares the output for GitHub Pages. Default: false. */
    public abstract Property<Boolean> getGithubPages();

    /** The address the site will be published at. Enables {@code 404.html}, {@code sitemap.xml} and canonical links. */
    public abstract Property<String> getBaseUrl();

    /** The custom domain of a GitHub Pages site. */
    public abstract Property<String> getCustomDomain();

    /** Fails the build if generating the site produced warnings, e.g. an unresolved {@code {@link}}. Default: false. */
    public abstract Property<Boolean> getFailOnWarnings();

    /** Further arguments for {@code doctize generate}. Default: those of the {@code doctizeArgs} project property. */
    public abstract ListProperty<String> getArguments();

    /** The Java the tool runs on, which has to be 25 or newer. Default: a Java 25 toolchain. */
    public abstract Property<JavaLauncher> getJavaLauncher();
}
