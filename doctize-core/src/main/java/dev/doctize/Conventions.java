package dev.doctize;

import dev.doctize.model.SourceModelJson;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What Doctize finds by itself when a setting is left out, so that a project with the usual layout needs no configuration:
 * the guides in {@code docs}, the processor's output in the class directory of Gradle or Maven, and the repository in the
 * Git remote.
 */
final class Conventions {

    static final String GUIDES_DIRECTORY = "docs";
    static final List<String> CLASS_DIRECTORIES = List.of("build/classes/java/main", "target/classes");

    /** Only the address of a public forge is published without being asked for: an internal host name stays internal. */
    private static final Set<String> PUBLIC_HOSTS = Set.of("github.com", "gitlab.com", "bitbucket.org", "codeberg.org");
    private static final Pattern SECTION = Pattern.compile("^\\s*\\[\\s*remote\\s+\"([^\"]+)\"\\s*]\\s*$");
    private static final Pattern OTHER_SECTION = Pattern.compile("^\\s*\\[.*]\\s*$");
    private static final Pattern URL = Pattern.compile("^\\s*url\\s*=\\s*(\\S+)\\s*$");
    /** {@code git@github.com:acme/cache.git}: no scheme, a colon between host and path. */
    private static final Pattern SCP_LIKE = Pattern.compile("^(?:[^@/]+@)?([^:/]+):(?!//)(.+)$");

    private Conventions() {}

    /** The {@code docs} directory, if the project has one. */
    static Optional<Path> guides(Path baseDirectory) {
        Path directory = baseDirectory.resolve(GUIDES_DIRECTORY);
        return Files.isDirectory(directory) ? Optional.of(directory) : Optional.empty();
    }

    /** The class directories of Gradle and Maven that contain a documentation model. */
    static List<Path> models(Path baseDirectory) {
        return CLASS_DIRECTORIES.stream()
                .map(baseDirectory::resolve)
                .filter(directory -> Files.isRegularFile(directory.resolve(SourceModelJson.RESOURCE_PATH)))
                .toList();
    }

    /**
     * The web address of the repository the directory belongs to, from the remote {@code origin} (or else the first remote) of
     * its Git configuration. Only addresses on public forges are returned, as {@code https} URLs without credentials.
     */
    static Optional<String> repository(Path baseDirectory) {
        return gitConfig(baseDirectory.toAbsolutePath().normalize())
                .flatMap(Conventions::remote)
                .map(Remote::url)
                .flatMap(Conventions::webAddress);
    }

    /** A remote of the Git configuration. */
    private record Remote(String name, String url) {}

    /**
     * The revision that links to the sources point to, so that they work for readers of the repository's web site: the checked-out
     * branch if the remote has it; else the default branch of the remote, since the local branch was never pushed; and the commit
     * if no branch is checked out, as in most continuous integration builds.
     */
    static Optional<String> revision(Path baseDirectory) {
        Optional<Path> gitDir = gitDirectory(baseDirectory.toAbsolutePath().normalize());
        if (gitDir.isEmpty()) {
            return Optional.empty();
        }
        String head = existing(gitDir.get().resolve("HEAD")).map(file -> read(file).strip()).orElse("");
        if (head.isEmpty()) {
            return Optional.empty();
        }
        if (!head.startsWith("ref:")) {
            return Optional.of(head);
        }
        String branch = head.substring(4).strip().replaceFirst("^refs/heads/", "");
        Path common = commonDirectory(gitDir.get());
        Optional<String> remote = existing(common.resolve("config")).flatMap(Conventions::remote).map(Remote::name);
        if (remote.isEmpty() || hasRef(common, "refs/remotes/" + remote.get() + "/" + branch)) {
            return Optional.of(branch);
        }
        return Optional.of(defaultBranch(common, remote.get()).orElse(branch));
    }

    /** What {@code refs/remotes/<remote>/HEAD} points to, else {@code main} or {@code master} if the remote has it. */
    private static Optional<String> defaultBranch(Path common, String remote) {
        String prefix = "refs/remotes/" + remote + "/";
        Optional<String> head = existing(common.resolve(prefix + "HEAD"))
                .map(file -> read(file).strip())
                .filter(ref -> ref.startsWith("ref: " + prefix))
                .map(ref -> ref.substring(("ref: " + prefix).length()));
        return head.or(() -> Stream.of("main", "master").filter(name -> hasRef(common, prefix + name)).findFirst());
    }

    /** A ref is a file below the Git directory, or a line of {@code packed-refs}. */
    private static boolean hasRef(Path common, String ref) {
        return Files.isRegularFile(common.resolve(ref))
                || existing(common.resolve("packed-refs")).stream()
                        .flatMap(file -> read(file).lines())
                        .anyMatch(line -> line.endsWith(" " + ref));
    }

    private static Optional<Path> gitConfig(Path directory) {
        return gitDirectory(directory).flatMap(gitDir -> existing(commonDirectory(gitDir).resolve("config")));
    }

    /** The Git directory of the working tree; a worktree or submodule has a {@code .git} file that points to it. */
    private static Optional<Path> gitDirectory(Path directory) {
        for (Path current = directory; current != null; current = current.getParent()) {
            Path git = current.resolve(".git");
            if (Files.isDirectory(git)) {
                return Optional.of(git);
            }
            if (Files.isRegularFile(git)) {
                String content = read(git).strip();
                return content.startsWith("gitdir:")
                        ? Optional.of(current.resolve(content.substring("gitdir:".length()).strip()).normalize())
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** A worktree shares the configuration of the main working tree. */
    private static Path commonDirectory(Path gitDir) {
        Path commonDir = gitDir.resolve("commondir");
        return Files.isRegularFile(commonDir) ? gitDir.resolve(read(commonDir).strip()).normalize() : gitDir;
    }

    /** The remote {@code origin}, or else the first remote. */
    private static Optional<Remote> remote(Path config) {
        String remote = null;
        Remote first = null;
        for (String line : read(config).lines().toList()) {
            Matcher section = SECTION.matcher(line);
            if (section.matches()) {
                remote = section.group(1);
                continue;
            }
            if (OTHER_SECTION.matcher(line).matches()) {
                remote = null;
                continue;
            }
            Matcher url = URL.matcher(line);
            if (remote != null && url.matches()) {
                if (remote.equals("origin")) {
                    return Optional.of(new Remote(remote, url.group(1)));
                }
                first = first == null ? new Remote(remote, url.group(1)) : first;
            }
        }
        return Optional.ofNullable(first);
    }

    /** {@code git@github.com:acme/cache.git}, {@code ssh://git@github.com/acme/cache} ... become {@code https://github.com/acme/cache}. */
    static Optional<String> webAddress(String remote) {
        String host;
        String path;
        Matcher scp = SCP_LIKE.matcher(remote);
        if (!remote.contains("://") && scp.matches()) {
            host = scp.group(1);
            path = scp.group(2);
        } else {
            try {
                URI uri = new URI(remote);
                host = uri.getHost();
                path = uri.getPath();
            } catch (URISyntaxException e) {
                return Optional.empty();
            }
        }
        if (host == null || path == null || !PUBLIC_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        path = path.replaceAll("^/+|/+$", "").replaceFirst("\\.git$", "");
        if (!path.contains("/")) {
            return Optional.empty();
        }
        return Optional.of("https://" + host.toLowerCase(Locale.ROOT) + "/" + path);
    }

    private static Optional<Path> existing(Path file) {
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /** The conventions are a convenience: an unreadable file means there is nothing to find, not an error. */
    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return "";
        }
    }
}
