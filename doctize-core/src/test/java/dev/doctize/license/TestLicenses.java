package dev.doctize.license;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * Issues licenses for the tests, with a key pair made for the test. It writes the format down independently of the
 * code that reads it, so a change of the format has to be a deliberate one.
 */
public final class TestLicenses {

    public static final LocalDate TODAY = LocalDate.of(2026, 9, 20);
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);

    private final KeyPair keys;

    public TestLicenses() {
        try {
            this.keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public LicenseVerifier verifier() {
        return new LicenseVerifier(keys.getPublic(), CLOCK);
    }

    /** A license that another key signed, which a verifier of this test's key must refuse. */
    public static String signedByAStranger(License license) {
        return sign(license.toJson(), new TestLicenses());
    }

    /** A license signed by this test's key. */
    public String sign(License license) {
        return sign(license.toJson(), this);
    }

    public String sign(byte[] content) {
        return sign(content, this);
    }

    private static String sign(byte[] content, TestLicenses signer) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(signer.keys.getPrivate());
            signature.update(content);
            Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
            return url.encodeToString(content) + "." + url.encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static License license(java.util.Set<Feature> features, LocalDate expires) {
        return new License("L-1", "Acme Corp", LocalDate.of(2026, 1, 1), expires, features);
    }
}
