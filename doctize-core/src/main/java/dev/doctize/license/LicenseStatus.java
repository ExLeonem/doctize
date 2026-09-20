package dev.doctize.license;

import dev.doctize.annotations.Doc;
import java.util.Optional;

/**
 * The outcome of looking for a license: there is none, there is one that cannot be used, or there is a valid one.
 * Ask it whether a {@link Feature} may be used with {@link #denialFor(Feature)}.
 */
@Doc(group = "Licensing", order = 65)
public final class LicenseStatus {

    private final License license;
    private final String origin;
    private final String problem;

    private LicenseStatus(License license, String origin, String problem) {
        this.license = license;
        this.origin = origin;
        this.problem = problem;
    }

    /** No license was found. */
    public static LicenseStatus absent() {
        return new LicenseStatus(null, "", "");
    }

    public static LicenseStatus valid(License license, String origin) {
        return new LicenseStatus(license, origin, "");
    }

    public static LicenseStatus invalid(String problem, String origin) {
        return new LicenseStatus(null, origin, problem);
    }

    /**
     * Looks for a license and checks it.
     *
     * @param found what {@link LicenseLocator} found
     * @param verifier the check to apply
     */
    public static LicenseStatus of(Optional<LicenseLocator.Found> found, LicenseVerifier verifier) {
        if (found.isEmpty()) {
            return absent();
        }
        try {
            return valid(verifier.verify(found.get().text()), found.get().origin());
        } catch (LicenseException e) {
            return invalid(e.getMessage(), found.get().origin());
        }
    }

    /** The license, if there is a valid one. */
    public Optional<License> license() {
        return Optional.ofNullable(license);
    }

    /**
     * @return why the feature cannot be used, or empty if it can. The reason is a full sentence for the user.
     */
    public Optional<String> denialFor(Feature feature) {
        if (license != null) {
            return license.allows(feature)
                    ? Optional.empty()
                    : Optional.of("The license of " + license.licensee() + " does not include the " + feature.title() + ", which need a Pro license.");
        }
        if (!problem.isEmpty()) {
            return Optional.of("The " + feature.title() + " need a valid Doctize Pro license, but the license from " + origin
                    + " cannot be used. " + problem);
        }
        return Optional.of("The " + feature.title() + " need a Doctize Pro license, and none was found. Pass one with --license,"
                + " set " + LicenseLocator.ENVIRONMENT_VARIABLE + ", or put " + LicenseLocator.FILE_NAME + " next to doctize.yml.");
    }

    /** What is wrong with the license that was found; empty if it is valid or if there is none. */
    public String problem() {
        return problem;
    }

    /** Where the license came from, e.g. {@code the file doctize.license}; empty if there is none. */
    public String origin() {
        return origin;
    }
}
