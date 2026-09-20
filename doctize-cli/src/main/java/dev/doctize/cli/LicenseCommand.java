package dev.doctize.cli;

import dev.doctize.DoctizeException;
import dev.doctize.license.Feature;
import dev.doctize.license.LicenseLocator;
import dev.doctize.license.LicenseStatus;
import dev.doctize.license.LicenseVerifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code doctize license}: shows which license is used and what it unlocks. */
@Command(name = "license", description = "Shows the license that is in use and the features it unlocks.", mixinStandardHelpOptions = true)
final class LicenseCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, description = "The configuration file; the license file is looked for next to it.")
    private Path config;

    @Option(names = "--license", description = "The license file to check. Default: the one Doctize would use.")
    private Path license;

    @Override
    public Integer call() {
        try {
            Path base = config != null ? config.toAbsolutePath().getParent() : Path.of("").toAbsolutePath();
            Optional<LicenseLocator.Found> found = LicenseLocator.locate(
                    license, null, base, System.getenv(), Path.of(System.getProperty("user.home")));
            LicenseStatus status = LicenseStatus.of(found, LicenseVerifier.vendor());
            if (found.isEmpty()) {
                System.out.println("No license found. Doctize is free to use; a license unlocks: "
                        + Arrays.stream(Feature.values()).map(Feature::title).toList());
                System.out.println("Pass a license with --license, set " + LicenseLocator.ENVIRONMENT_VARIABLE
                        + ", or put " + LicenseLocator.FILE_NAME + " next to doctize.yml.");
                return 1;
            }
            System.out.println("License from " + found.get().origin());
            if (status.license().isEmpty()) {
                System.out.println("Not usable: " + status.problem());
                return 1;
            }
            System.out.println("Licensed to " + status.license().get().summary());
            for (Feature feature : Feature.values()) {
                System.out.println("  " + feature.id() + ": " + (status.license().get().allows(feature) ? "unlocked" : "not included"));
            }
            return 0;
        } catch (DoctizeException e) {
            System.err.println("doctize: " + e.getMessage());
            return 1;
        }
    }
}
