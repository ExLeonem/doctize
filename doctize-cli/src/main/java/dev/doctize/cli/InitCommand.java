package dev.doctize.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** {@code doctize init}: creates a starter configuration and guides. */
@Command(name = "init", description = "Creates a doctize.yml and starter guides.", mixinStandardHelpOptions = true)
final class InitCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "Project directory. Default: current directory.", defaultValue = ".")
    private Path directory;

    @Option(names = "--name", description = "Project name. Default: the directory name.")
    private String name;

    @Option(names = "--github-pages", description = "Also create a GitHub Actions workflow that publishes the site to GitHub Pages.")
    private boolean githubPages;

    @Override
    public Integer call() throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        String projectName = name != null ? name : String.valueOf(root.getFileName());
        Path config = root.resolve("doctize.yml");
        if (Files.exists(config)) {
            System.err.println("doctize: " + config + " already exists, nothing was changed.");
            return 1;
        }
        for (Map.Entry<String, String> file : StarterFiles.of(projectName, githubPages).entrySet()) {
            Path target = root.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
            System.out.println("created " + root.relativize(target));
        }
        System.out.println("Next: annotate types with @Doc, then run `doctize generate`.");
        if (githubPages) {
            System.out.println("Then set project.repository in doctize.yml, and choose \"GitHub Actions\" as the source in the repository's Pages settings.");
        }
        return 0;
    }
}
