package dev.doctize.license;

import dev.doctize.annotations.Doc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the license of a run. The first of these that exists is used:
 *
 * <ol>
 *   <li>a license key or file given explicitly,
 *   <li>the environment variable {@code DOCTIZE_LICENSE}, which holds the license itself (handy for CI secrets),
 *   <li>the file {@code doctize.license} in the base directory, next to {@code doctize.yml},
 *   <li>the file {@code .doctize/license} in the home directory of the user.
 * </ol>
 */
@Doc(group = "Licensing", order = 63)
public final class LicenseLocator {

    public static final String ENVIRONMENT_VARIABLE = "DOCTIZE_LICENSE";
    public static final String FILE_NAME = "doctize.license";

    /**
     * A license that was found, not yet checked.
     *
     * @param text the content of the license
     * @param origin where it came from, for messages
     */
    public record Found(String text, String origin) {}

    private LicenseLocator() {}

    /**
     * @param explicitFile a license file the user named, or {@code null}
     * @param explicitKey the text of a license the user supplied, or {@code null}
     * @param baseDirectory where {@code doctize.license} is looked for
     * @param environment the environment variables
     * @param home the home directory of the user, or {@code null}
     * @return the license, or empty if there is none
     * @throws LicenseException if a file that was named explicitly cannot be read
     */
    public static Optional<Found> locate(
            Path explicitFile, String explicitKey, Path baseDirectory, Map<String, String> environment, Path home) {
        if (explicitKey != null && !explicitKey.isBlank()) {
            return Optional.of(new Found(explicitKey, "the license key that was passed"));
        }
        if (explicitFile != null) {
            if (!Files.isRegularFile(explicitFile)) {
                throw new LicenseException("The license file " + explicitFile + " does not exist.");
            }
            return Optional.of(read(explicitFile));
        }
        String fromEnvironment = environment.get(ENVIRONMENT_VARIABLE);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return Optional.of(new Found(fromEnvironment, "the environment variable " + ENVIRONMENT_VARIABLE));
        }
        for (Path candidate : new Path[] {baseDirectory.resolve(FILE_NAME), home == null ? null : home.resolve(".doctize").resolve("license")}) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                return Optional.of(read(candidate));
            }
        }
        return Optional.empty();
    }

    private static Found read(Path file) {
        try {
            return new Found(Files.readString(file), "the file " + file);
        } catch (IOException e) {
            throw new LicenseException("Cannot read the license file " + file + ": " + e.getMessage(), e);
        }
    }
}
