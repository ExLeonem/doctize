package dev.doctize.testing;

import dev.doctize.license.Feature;
import dev.doctize.license.License;
import dev.doctize.license.LicenseVerifier;
import dev.doctize.licensing.LicenseIssuer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;

/** Licenses for the tests that span modules. */
public final class TestLicenses {

    private TestLicenses() {}

    /** A key pair made for one test, and the verifier that trusts it. In-process tests give the verifier to {@code Doctize}. */
    public record Ephemeral(KeyPair keys, LicenseVerifier verifier) {

        public static Ephemeral create() {
            KeyPair keys = LicenseIssuer.generateKeys();
            return new Ephemeral(keys, new LicenseVerifier(keys.getPublic(), Clock.systemUTC()));
        }

        /** The text of a license for all features, without an expiry, signed with this key. */
        public String licenseText() {
            return issue(keys.getPrivate(), EnumSet.allOf(Feature.class), null);
        }
    }

    public static String issue(PrivateKey key, EnumSet<Feature> features, LocalDate expires) {
        return LicenseIssuer.issue(new License("test", "Test Licensee", LocalDate.now().minusDays(1), expires, features), key);
    }

    /**
     * The private key of the vendor, if this machine has it. Processes that use the real, installed product can only be
     * given a license that the vendor's public key accepts, so their licensed path is tested where the key is.
     */
    public static Optional<PrivateKey> vendorKey() {
        String location = System.getProperty("doctize.license.private-key");
        if (location == null || !Files.isRegularFile(Path.of(location))) {
            return Optional.empty();
        }
        try {
            return Optional.of(LicenseIssuer.readPrivateKey(Files.readString(Path.of(location))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes a license file for all features, signed with the vendor's key. */
    public static Path writeVendorLicense(Path file, PrivateKey key) {
        try {
            return Files.writeString(file, issue(key, EnumSet.allOf(Feature.class), LocalDate.now().plusDays(30)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
